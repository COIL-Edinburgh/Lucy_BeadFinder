/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package bio.coil.CoilEdinburgh;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.Thresholder;
import ij.plugin.ZAxisProfiler;
import ij.plugin.ZProjector;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import io.scif.*;
import io.scif.services.DatasetIOService;
import io.scif.services.FormatService;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imagej.roi.ROIService;
import net.imglib2.IterableInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.roi.MaskInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.apache.commons.io.FilenameUtils;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import java.io.IOException;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * This example illustrates how to create an ImageJ {@link Command} plugin.
 * <p>
 * </p>
 * <p>
 * You should replace the parameter fields with your own inputs and outputs,
 * and replace the {@link run} method implementation with your own logic.
 * </p>
 */
@Plugin(type = Command.class, menuPath = "Plugins>Users Plugins>Lucy Bead Finder")
public class Lucy_BeadFinder<T extends RealType<T>> implements Command {
    //
    // Feel free to add more parameters here...
    //
    @Parameter
    private FormatService formatService;

    @Parameter
    private DatasetIOService datasetIOService;

    @Parameter
    private UIService uiService;

    @Parameter
    private OpService ops;

    @Parameter
    private ROIService roiService;

    @Parameter(label = "Open Folder: ", style="directory")
    public File filePath;

    RoiManager roiManager;
    double pixelSize;

    String filename;
    @Override
    public void run() {

            File[] files = filePath.listFiles();
            roiManager = new RoiManager();

            for (File file : files) {
                if (file.toString().contains(".dv") && !file.toString().contains("D3D") && !file.toString().contains("log") && !file.toString().contains("Overlay")) {
                    //Open file and get filename and filepath
                    Img<T> img = openDataset(file);
                    uiService.show(img);
                    filename = FilenameUtils.removeExtension(file.getName());
                    ImagePlus imp = ImageJFunctions.wrap(img, "Original");

                    //Min project
                    ImagePlus min = ZProjector.run(imp, "Minimum");
                    min.show();

                    //Bandpass Filter
                    IJ.run(min, "Bandpass Filter...", "filter_large=40 filter_small=10 suppress=None tolerance=5 autoscale saturate");
                    min.show();

                    //Analyse
                    IJ.setAutoThreshold(min, "Triangle");
                    IJ.run(min, "Analyze Particles...", "size=700-Infinity pixel circularity=00-1.00 show=Masks");
                    ImagePlus masks = WindowManager.getCurrentImage();

                    //Watershed and Erode
                    Prefs.blackBackground = false;
                    IJ.run(masks, "Watershed", "");
                    IJ.run(masks, "Erode", "");

                    //Analyse (ROIs)
                    IJ.run(masks, "Analyze Particles...", "size=500-Infinity pixel circularity=0.75-1.00 show=Masks");
                    ImagePlus overlay = WindowManager.getCurrentImage();
                    IJ.run("16-bit", "");
                    overlay.setTitle("Overlay");

                    ImagePlus max = ZProjector.run(imp, "Maximum");
                    max.show();
                    max.setTitle("Maximum");

                    String outputFile = createDirectory(filename);
                    IJ.save(max, Paths.get(outputFile, filename + "_Max.tif").toString());
                    IJ.save(overlay, Paths.get(outputFile, filename + "_Overlay.tif").toString());
                    IJ.run("Merge Channels...", "c1=[Overlay] c4=[Maximum] create");
                    ImagePlus merge = WindowManager.getCurrentImage();
                    IJ.save(merge, Paths.get(outputFile, filename + "_Merge.tif").toString());
                    IJ.run("Close All", "");
                }
            }
        }

        private String createDirectory(String filename) {

            String newDirectory = Paths.get(filePath.toString(),filename).toString();
            File tmpDir = new File(newDirectory);
            if (!tmpDir.exists()) {
                new File(newDirectory).mkdir();
            }
            return newDirectory;
        }

    private void drawNumbers(int Counter, ImagePlus ProjectedWindow, Roi roi) {
        ImageProcessor ip = ProjectedWindow.getProcessor();
        Font font = new Font("SansSerif", Font.PLAIN, 12);
        ip.setFont(font);
        ip.setColor(Color.white);
        String cellnumber = String.valueOf(Counter);
        ip.draw(roi);
        ip.drawString(cellnumber, (int) roi.getContourCentroid()[0], (int) roi.getContourCentroid()[1]);
        ProjectedWindow.updateAndDraw();
    }

    private void cursorOutline(Img<T> image, Roi[] rois, int value){

        for (Roi roi : rois) {
            MaskInterval maskIntKin = roiService.toMaskInterval(roi);
            IterableInterval<T> maskKin = Views.interval(image, maskIntKin);
            net.imglib2.Cursor<T> cursorKin = maskKin.localizingCursor();
            colorIn(maskKin, cursorKin, roi, value);
        }

    }

    private void colorIn(IterableInterval<T> mask, net.imglib2.Cursor<T> cursor, Roi roi, int value ) {
        for (int k = 0; k < mask.size(); k++) {
            //RealType<T> value = cursor.get();
            int x = (int) cursor.positionAsDoubleArray()[0];
            int y = (int) cursor.positionAsDoubleArray()[1];

            //If the pixel is in the bounding ROI
            if (roi.contains(x, y)) {
                cursor.get().setReal(value);
            }
            //Move the cursors forwards
            cursor.fwd();
        }
    }

           public Img<T> openDataset(File dataset) {
            Dataset imageData = null;
            String filePath = dataset.getPath();
            try {
                imageData = datasetIOService.open(filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Map<String, Object> prop = imageData.getProperties();
            DefaultImageMetadata metaData = (DefaultImageMetadata) prop.get("scifio.metadata.image");
            pixelSize = metaData.getAxes().get(0).calibratedValue(1);
            assert imageData != null;

            return (Img<T>)imageData.getImgPlus();
        }



    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        ij.command().run(Lucy_BeadFinder.class, true);
    }

}

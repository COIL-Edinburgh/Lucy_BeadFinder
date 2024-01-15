# A plugin to show the localisation of magnetic beads imaged with DNA

- This plugin takes as in put an input folder of .dv files and attempts to open any files with ".dv" extension and not 
  "log", "D3D" or "Output" in the file name.
- A minimum projection of the 3D stack is performed
- A bandpass filter is applied with settings; "filter_large=40 filter_small=10 suppress=None tolerance=5 autoscale saturate"
- "Triangle" thrshold is applied and particles are analysed and a mask of areas > 700 pixels is produced 
- The mask is split with "Watershed" and then "Erode" is applied.
- A new mask is created of particles > 500 pixels and with circularity 0.75-1.00
- In an output folder (filename) are 3 images, the final mask, a max projection of the original image and a merge of the two.

## Installation
Save the Lucy_BeadFinder-1.0.2.jar file in the plugins folder of your ImageJ installation. ImageJ should be version 1.5 
or later and should have BioFormats installed. Lucy Bead Finder should appear in the Plugins menu (probably at the very bottom).

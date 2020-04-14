# CMPT 365 - Final Project
The goal of this project is to implement algorithms that construct "spatio-temporal" images (STIs) which can be used to detect and characterize video transitions. Specifically we are interested in detecting cuts and wipes. We created the STIs using the STI by copying pixels method as well as by histogram differeces.
# Installation
To run the program you can go to command prompt if you are on Windows and type in this command.  
`java -jar -Djava.library.path="C:\path\to\opencv\jars" 365proj.jar`  
For example, on my computer I would type  
`java -jar -Djava.library.path="C:\Users\ejc5\Downloads\opencv\build\java\x64" 365proj.jar`
# Usage
To use the program simply click the open button to select a video clip you would like to use and click play to see the generated spatio-temporal images.
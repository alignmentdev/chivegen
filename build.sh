# A very small shell script to recompile FicArchiveBuilder with one command

cd bin
echo "Removing old binaries..."
rm -f *.class
cd ../src
echo "Compiling..."
javac ChiveGenMain.java -d ../bin
cd ..
echo "Done."

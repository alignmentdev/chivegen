#!/bin/bash

# A very small shell script to recompile FicArchiveBuilder with one command

if [ ! -d ./bin ]; then
	# check that binary folder exists, create it if not
	echo "Creating folder $(pwd)/bin..."
	mkdir ./bin
fi;
cd ./bin
echo "Removing any old binaries..."
rm -f *.class
cd ../src
echo "Compiling..."
javac ChiveGenMain.java -d ../bin
cd ..
echo "Done."

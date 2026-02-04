# ChiveGen

A static site generator for fanfiction archives. Check out docs_beta.txt for more info.

## Setup

To compile ChiveGen, compile the source files with `javac ChiveGenMain.java`, with the output destination as wherever you want your compiled java files to go. (Ideally, this should be a folder `bin` in the root of the repo directory.)

The included `build.sh` script assumes the existence of a folder called `bin` in the same directory as `src`. If you do not want your binaries to go there, **do not use it**. It will delete any .class files already in `./bin`, and output new ChiveGen binaries there!

To run ChiveGen, run `chivegen [args]` in the root folder of the repository. Like with `build.sh`, it assumes your binaries will live in a `./bin` directory relative to it, so if you don't do that, you _will_ need to change it to look in a different directory.

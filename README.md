# ChiveGen

[https://alignmentdev.github.io/chivegen-site/](https://alignmentdev.github.io/chivegen-site/)

A static site generator for online fiction archives. Check out DOCS_BETA.txt for more info.

You can also see a demo archive here: [https://alignmentdev.github.io/chivegen-site/demo/](https://alignmentdev.github.io/chivegen-site/demo/)

## Setup

You can compile ChiveGen in two ways: either compile directly in the `src` directory with `javac ChiveGenMain.java`, or run the script `build.sh` from the root of the ChiveGen repository.

The included `build.sh` script will output binaries to `./bin`, and if it doesn't find this folder, it will make one. If you do not want your binaries to go there, **do not use it**. It will delete any .class files already in `./bin`, and output new ChiveGen binaries there!

## Usage

To run ChiveGen, run `./chivegen [args]` in the root folder of the repository. Like with `build.sh`, it assumes your binaries will live in a `./bin` directory relative to it. To have it look elsewhere you _will_ need to manually change the `chivegen` script to look in a different directory.

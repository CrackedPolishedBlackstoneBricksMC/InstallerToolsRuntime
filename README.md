**⚠⚠⚠ this doesn't work don't use it ⚠⚠⚠**

# InstallerToolsRuntime

Sketch of a fully binary-based toolchain for Neoforge. Unlike NeoForm, which uses a Java decompiler, InstallerToolsRuntime runs the official NeoForge installer ~~and packages the results as something you can consume from a dev workspace~~. This works becasue the official installers ship and handle binpatches, just like Forge 1.6; although since binpatching is done at the *end* of the remapping process, you are not able to configure anything before that stage.

Basically it's [voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom/) for NeoForge. [Forgewrapper](https://github.com/ZekerZhayard/ForgeWrapper) probably counts as prior art.

The name is simply a bad spoof of [NeoFormRuntime](https://github.com/neoforged/neoformruntime) since it looks like the NeoForge installer is implemented with something called "installertools". I know it's a shitty name.

## Fate

I'm using this to experiment, but when [toybox](https://github.com/CrackedPolishedBlackstoneBricksMC/toybox/) is more complete I will move the code into there.

## Status

It's able to run the one (1) version of the NeoForge installer that I've tried.

Might actually work in a larger project ?? I've done some automated testing but no "does it feel right" testing

### notes to self

* neo seems to patch in all the client-only classes on the server (!) so a jar merger is not necessary. I can just use the client jar
* after running the installer i should produce a "bill of materials", simply one filepath per line, listing paths to the client jar, neoforge universal, and all the dependent libraries
* at configure time i check for existence of this file, if it exists i feed its contents to `project.files()`, if it doesn't i run the installer and save the bom there
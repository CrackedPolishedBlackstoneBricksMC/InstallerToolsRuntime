**⚠⚠⚠ this doesn't work don't use it ⚠⚠⚠**

# InstallerToolsRuntime

Sketch of a fully binary-based toolchain for Neoforge. Unlike NeoForm, which uses a Java decompiler, InstallerToolsRuntime runs the official NeoForge installer ~~and packages the results as something you can consume from a dev workspace~~. This works becasue the official installers ship and handle binpatches, just like Forge 1.6; although since binpatching is done at the *end* of the remapping process, you are not able to configure anything before that stage.

Basically it's [voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom/) for NeoForge. [Forgewrapper](https://github.com/ZekerZhayard/ForgeWrapper) probably counts as prior art.

The name is simply a bad spoof of [NeoFormRuntime](https://github.com/neoforged/neoformruntime) since it looks like the NeoForge installer is implemented with something called "installertools". I know it's a shitty name.

## Fate

I'm using this to experiment, but when [toybox](https://github.com/CrackedPolishedBlackstoneBricksMC/toybox/) is more complete I will move the code into there.

## Status

It's able to run the one (1) version of the NeoForge installer that I've tried.

* The results simply litter your `~/.m2/repository`; it doesn't put the results on the compilation classpath yet.
* There is no caching, so you rerun the installer every time you invoke gradle.
* I also need to include a jar merger to zip the client and server together. (Which is part of why I want to bring this code into Toybox, because the jar merger should live in there.)

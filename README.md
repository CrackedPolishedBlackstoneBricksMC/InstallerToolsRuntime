# InstallerToolsRuntime

*Sketch* of a fully binary-based toolchain for Neoforge.

Unlike NeoForm, which uses a Java decompiler, InstallerToolsRuntime runs the official NeoForge installer and packages the results as something you can consume from a dev workspace. This works becasue the official installers ship and handle binpatches, just like Forge 1.6; although since binpatching is done at the *end* of the remapping process, you are not able to configure anything before that stage.

Basically it's [voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom/) × [Forgewrapper](https://github.com/ZekerZhayard/ForgeWrapper). The name is simply a bad spoof of [NeoFormRuntime](https://github.com/neoforged/neoformruntime), since it looks like the NeoForge installer is largely backed by something called "installertools". I know it's a shitty name.

## Fate

I'm using this to experiment, but when [toybox](https://github.com/CrackedPolishedBlackstoneBricksMC/toybox/) is more complete I will move the code into there, and maybe flesh it out into a general purpose "running modloader installers" tool. Maybe this idea works on forge 1.7 or even fabric (idk why you'd do that though). Needs research

## Status

It's able to run the one (1) version of the NeoForge installer that I've tried without crashing, and the files look ok at first blush. Might actually work in a larger project ?? I've done some automated testing but no "does it feel right" testing

There is no Minecraft launcher component, there is no fancy mixin glitter, there are no access wideners

Also the plugin is missing features still, like a [minivan](https://github.com/CrackedPolishedBlackstoneBricksMC/minivan/)nish lower-level api for being specific about which configurations your neoforge artifacts end up in

### notes to self

* neo seems to patch in all the client-only classes on the server (!) so a jar merger is not necessary. can just use the client jar
* check how hard this explodes on minecraft forge 1.20.1, and possibly even older versions; i think `net.neoforged` is still hardcoded once or twice, and surely the abi has changed over the years
# GrowAPet HUD resource pack

`GrowAPet-HUD` is the source directory for the Minecraft 1.21.11 HUD pack. Zip the **contents** of that directory so `pack.mcmeta` is at the root of the ZIP, host the ZIP, and configure it as the server resource pack.

The actionbar remains readable with Unicode fallback icons until the client reports that the server resource pack loaded successfully. After that event, GrowAPet uses the `growapet:hud` atlas sprites automatically.

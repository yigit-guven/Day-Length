package net.yigitguven.daylength.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

public class DayLengthModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (FabricLoader.getInstance().isModLoaded("cloth-config2")) {
            return DayLengthConfigScreen::createConfigScreen;
        }
        return parent -> null;
    }
}

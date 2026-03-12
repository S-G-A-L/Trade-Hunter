package com.tradehunter;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;

import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import org.lwjgl.glfw.GLFW;

import java.io.Reader;
import java.io.Writer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class TradeHunterClient implements ClientModInitializer {

    private static KeyMapping toggleKey;

    private static boolean running = false;

    private static int cycleDelay = 0;

    private static final List<TargetEnchant> targets = new ArrayList<>();

    private static final Path CONFIG =
            Paths.get("config/tradehunter_targets.json");

    static class TargetEnchant {

        String id;
        int level;
        int maxPrice;

        TargetEnchant(String id, int level, int maxPrice) {

            this.id = id;
            this.level = level;
            this.maxPrice = maxPrice;

        }

    }

    private static void chat(String message) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.gui != null) {
            mc.gui.getChat().addMessage(Component.literal(message));
        }
    }

    @Override
    public void onInitializeClient() {

        loadTargets();

        toggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.tradehunter.toggle",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_H,
                        "key.categories.misc"
                )
        );

        registerCommands();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            while (toggleKey.consumeClick()) {

                running = !running;

                chat("TradeHunter running: " + running);

            }

            if (!running) return;

            Minecraft mc = Minecraft.getInstance();

            if (mc.screen instanceof MerchantScreen merchantScreen) {

                if (cycleDelay > 0) {

                    cycleDelay--;

                    return;

                }

                boolean foundTarget = false;

                MerchantMenu menu = merchantScreen.getMenu();

                MerchantOffers offers = menu.getOffers();

                for (MerchantOffer offer : offers) {

                    ItemStack result = offer.getResult();

                    if (!result.is(Items.ENCHANTED_BOOK))
                        continue;

                    ItemEnchantments enchants =
                            result.getOrDefault(
                                    DataComponents.STORED_ENCHANTMENTS,
                                    ItemEnchantments.EMPTY
                            );

                    int price = offer.getCostA().getCount();

                    for (var entry : enchants.entrySet()) {

                        String id =
                                entry.getKey()
                                        .unwrapKey()
                                        .get()
                                        .location()
                                        .toString();

                        int level = entry.getIntValue();

                        for (TargetEnchant target : targets) {

                            if (id.equals(target.id)
                                    && level >= target.level
                                    && price <= target.maxPrice) {

                                foundTarget = true;

                                chat(
                                        "FOUND TARGET -> " +
                                                id +
                                                " level " +
                                                level +
                                                " price " +
                                                price
                                );

                            }

                        }

                    }

                }

                if (!foundTarget) {

                    pressTradeCycleButton(merchantScreen);

                    cycleDelay = 5;

                } else {

                    running = false;

                    chat("Search stopped");

                }

            }

        });

    }

    private void registerCommands() {

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->

                dispatcher.register(

                        literal("th")

                                .then(literal("add")

                                        .then(argument("enchant", StringArgumentType.string())

                                                .then(argument("level", IntegerArgumentType.integer())

                                                        .then(argument("price", IntegerArgumentType.integer())

                                                                .executes(ctx -> {

                                                                    String ench =
                                                                            StringArgumentType.getString(ctx, "enchant");

                                                                    int level =
                                                                            IntegerArgumentType.getInteger(ctx, "level");

                                                                    int price =
                                                                            IntegerArgumentType.getInteger(ctx, "price");

                                                                    targets.add(
                                                                            new TargetEnchant(
                                                                                    "minecraft:" + ench,
                                                                                    level,
                                                                                    price
                                                                            )
                                                                    );

                                                                    saveTargets();

                                                                    chat(
                                                                            "Added target: " +
                                                                                    ench +
                                                                                    " level " +
                                                                                    level +
                                                                                    " price <= " +
                                                                                    price
                                                                    );

                                                                    return 1;

                                                                })

                                                        )

                                                )

                                        )

                                )

                                .then(literal("list")

                                        .executes(ctx -> {

                                            chat("Targets:");

                                            for (TargetEnchant t : targets) {

                                                chat(
                                                        t.id +
                                                                " level>=" +
                                                                t.level +
                                                                " price<=" +
                                                                t.maxPrice
                                                );

                                            }

                                            return 1;

                                        })

                                )

                                .then(literal("clear")

                                        .executes(ctx -> {

                                            targets.clear();

                                            saveTargets();

                                            chat("Targets cleared");

                                            return 1;

                                        })

                                )

                )

        );

    }

    private static void pressTradeCycleButton(MerchantScreen screen) {

        for (var widget : screen.children()) {

            if (widget.getClass().getName()
                    .equals("de.maxhenkel.tradecycling.gui.CycleTradesButton")) {

                try {

                    widget.getClass()
                            .getMethod("onPress")
                            .invoke(widget);

                } catch (Exception ignored) {
                }

            }

        }

    }

    private static void saveTargets() {

        try {

            Files.createDirectories(CONFIG.getParent());

            Gson gson =
                    new GsonBuilder()
                            .setPrettyPrinting()
                            .create();

            Writer writer =
                    Files.newBufferedWriter(CONFIG);

            gson.toJson(targets, writer);

            writer.close();

        } catch (Exception ignored) {
        }

    }

    private static void loadTargets() {

        try {

            if (!Files.exists(CONFIG))
                return;

            Gson gson = new Gson();

            Reader reader =
                    Files.newBufferedReader(CONFIG);

            TargetEnchant[] loaded =
                    gson.fromJson(reader, TargetEnchant[].class);

            targets.addAll(Arrays.asList(loaded));

            reader.close();

        } catch (Exception ignored) {
        }

    }

}
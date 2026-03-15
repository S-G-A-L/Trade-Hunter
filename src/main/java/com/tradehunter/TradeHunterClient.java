package com.tradehunter;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.option.KeyBinding;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import de.maxhenkel.tradecycling.net.CycleTradesPacket;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.screen.MerchantScreenHandler;

import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.Reader;
import java.io.Writer;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.registry.entry.RegistryEntry;

import net.minecraft.util.Identifier;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import org.lwjgl.glfw.GLFW;


public class TradeHunterClient implements ClientModInitializer {



    private static KeyBinding toggleKey;

    private static boolean running = false;

    private static int cycleDelay = 0;

    private static final List<TargetEnchant> targets = new ArrayList<>();

    private static final Path CONFIG = Paths.get("config/tradehunter_targets.json");



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

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player != null) {

            mc.inGameHud.getChatHud().addMessage(Text.literal(message));

        }

    }



    @Override

    public void onInitializeClient() {

        loadTargets();



        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(

                "key.tradehunter.toggle",

                InputUtil.Type.KEYSYM,

                GLFW.GLFW_KEY_H,

                "key.categories.misc"

        ));



        registerCommands();



        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            while (toggleKey.wasPressed()) {

                running = !running;

                chat("TradeHunter running: " + (running ? "§aON" : "§cOFF"));

            }



            if (!running || client.world == null) return;



            if (client.currentScreen instanceof MerchantScreen merchantScreen) {

                if (cycleDelay > 0) {

                    cycleDelay--;

                    return;

                }



                MerchantScreenHandler handler = merchantScreen.getScreenHandler();

                TradeOfferList offers = handler.getRecipes();

                boolean foundTarget = false;



                for (TradeOffer offer : offers) {

                    ItemStack result = offer.getSellItem();

                    if (!result.isOf(Items.ENCHANTED_BOOK)) continue;

                    ItemEnchantmentsComponent enchants = EnchantmentHelper.getEnchantments(result);

                    int price = offer.getOriginalFirstBuyItem().getCount();



                    for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchants.getEnchantmentEntries()) {

                        RegistryEntry<Enchantment> enchEntry = entry.getKey();

                        int level = entry.getIntValue();



                        Identifier idIdentifier = enchEntry.getKey()

                                .map(key -> key.getValue())

                                .orElseGet(() -> client.world.getRegistryManager()

                                        .getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT)

                                        .getId(enchEntry.value()));



                        if (idIdentifier != null) {

                            String id = idIdentifier.toString();



                            for (TargetEnchant target : targets) {



                                String targetId = target.id.contains(":") ? target.id : "minecraft:" + target.id;

                                if (id.equals(targetId) && level >= target.level && price <= target.maxPrice) {

                                    foundTarget = true;

                                    chat("§6[!] ENCONTRADO: §e" + id + " " + level + " §aPrecio: " + price);

                                }

                            }

                        }

                    }

                }



                if (!foundTarget) {

                    pressTradeCycleButton(merchantScreen);

                    cycleDelay = 10;

                } else {

                    running = false;

                    chat("§bBusqueda finalizada.");

                }

            }

        });

    }



    private void registerCommands() {

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->

                dispatcher.register(literal("th")

                        .then(literal("add")

                                .then(argument("enchant", StringArgumentType.string())

                                        .then(argument("level", IntegerArgumentType.integer())

                                                .then(argument("price", IntegerArgumentType.integer())

                                                        .executes(ctx -> {

                                                            String ench = StringArgumentType.getString(ctx, "enchant");

                                                            int level = IntegerArgumentType.getInteger(ctx, "level");

                                                            int price = IntegerArgumentType.getInteger(ctx, "price");



                                                            targets.add(new TargetEnchant("minecraft:" + ench, level, price));

                                                            saveTargets();

                                                            chat("Added target: " + ench + " level " + level + " price <= " + price);

                                                            return 1;

                                                        })

                                                )

                                        )

                                )

                        )

                        .then(literal("list").executes(ctx -> {

                            chat("Targets:");

                            for (TargetEnchant t : targets) {

                                chat(t.id + " level>=" + t.level + " price<=" + t.maxPrice);

                            }

                            return 1;

                        }))

                        .then(literal("clear").executes(ctx -> {

                            targets.clear();

                            saveTargets();

                            chat("Targets cleared");

                            return 1;

                        }))

                )

        );

    }



    private static void pressTradeCycleButton(MerchantScreen screen) {

        for (var widget : screen.children()) {

            if (widget.getClass().getName().contains("CycleTradesButton")) {

                try {

                    ClientPlayNetworking.send(new CycleTradesPacket());

                } catch (Exception ignored) {}
            }
        }
    }



    private static void saveTargets() {

        try {

            Files.createDirectories(CONFIG.getParent());

            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            Writer writer = Files.newBufferedWriter(CONFIG);

            gson.toJson(targets, writer);

            writer.close();

        } catch (Exception ignored) {}

    }



    private static void loadTargets() {

        try {

            if (!Files.exists(CONFIG)) return;

            Gson gson = new Gson();

            Reader reader = Files.newBufferedReader(CONFIG);

            TargetEnchant[] loaded = gson.fromJson(reader, TargetEnchant[].class);

            targets.addAll(Arrays.asList(loaded));

            reader.close();

        } catch (Exception ignored) {}

    }

}
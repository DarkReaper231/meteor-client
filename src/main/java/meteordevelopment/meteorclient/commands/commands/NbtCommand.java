/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.CompoundNbtTagArgumentType;
import meteordevelopment.meteorclient.systems.config.Config;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.NbtPathArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class NbtCommand extends Command {
    public NbtCommand() {
        super("nbt", "Modifies NBT data for an item, example: .nbt add {display:{Name:'{\"text\":\"$cRed Name\"}'}}");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("add").then(argument("nbt", CompoundNbtTagArgumentType.create()).executes(s -> {
            ItemStack stack = mc.player.getInventory().getMainHandStack();

            if (validBasic(stack)) {
                NbtCompound tag = CompoundNbtTagArgumentType.get(s);
                NbtCompound source = stack.getOrCreateNbt();

                if (tag != null) {
                    source.copyFrom(tag);
                    setStack(stack);
                } else {
                    error("Some of the NBT data could not be found, try using: " + Config.get().prefix.get() + "nbt set {nbt}");
                }
            }

            return SINGLE_SUCCESS;
        })));

        builder.then(literal("set").then(argument("nbt", CompoundNbtTagArgumentType.create()).executes(context -> {
            ItemStack stack = mc.player.getInventory().getMainHandStack();

            if (validBasic(stack)) {
                stack.setNbt(CompoundNbtTagArgumentType.get(context));
                setStack(stack);
            }

            return SINGLE_SUCCESS;
        })));

        builder.then(literal("remove").then(argument("nbt_path", NbtPathArgumentType.nbtPath()).executes(context -> {
            ItemStack stack = mc.player.getInventory().getMainHandStack();

            if (validBasic(stack)) {
                NbtPathArgumentType.NbtPath path = context.getArgument("nbt_path", NbtPathArgumentType.NbtPath.class);
                path.remove(stack.getNbt());
            }

            return SINGLE_SUCCESS;
        })));

        builder.then(literal("get").executes(context -> {
            ItemStack stack = mc.player.getInventory().getMainHandStack();

            if (stack.isEmpty()) {
                target(false);
            } else {
                NbtCompound tag = stack.getOrCreateNbt();

                getInfo(false, tag);
            }

            return SINGLE_SUCCESS;
        }));

        builder.then(literal("copy").executes(context -> {
            ItemStack stack = mc.player.getInventory().getMainHandStack();

            if (stack.isEmpty()) {
                target(true);
            } else {
                NbtCompound tag = stack.getOrCreateNbt();

                copyData(false, tag, null);
            }

            return SINGLE_SUCCESS;
        }));

        builder.then(literal("paste").executes(context -> {
            ItemStack stack = mc.player.getInventory().getMainHandStack();

            if (validBasic(stack)) {
                stack.setNbt(new CompoundNbtTagArgumentType().parse(new StringReader(mc.keyboard.getClipboard())));
                setStack(stack);
            }

            return SINGLE_SUCCESS;
        }));

        builder.then(literal("count").then(argument("count", IntegerArgumentType.integer(-127, 127)).executes(context -> {
            ItemStack stack = mc.player.getInventory().getMainHandStack();

            if (validBasic(stack)) {
                int count = IntegerArgumentType.getInteger(context, "count");
                stack.setCount(count);
                setStack(stack);
                info("Set mainhand stack count to %s.",count);
            }

            return SINGLE_SUCCESS;
        })));
    }

    private void target(boolean copy) {
        HitResult target = mc.crosshairTarget;
        if (target.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) target).getBlockPos();
            BlockEntity be = mc.player.world.getBlockEntity(pos);
            NbtCompound tag = (be != null) ? be.createNbt() : new NbtCompound();
            BlockState state = mc.player.world.getBlockState(pos);

            if (copy) {
                copyData(true, tag, state);
            } else {
                getInfo(false, tag);
                getInfo(true, state);
            }
        } else if (target.getType() == HitResult.Type.ENTITY) {
            NbtCompound tag = ((EntityHitResult) target).getEntity().writeNbt(new NbtCompound());

            if (copy) {
                copyData(false, tag, null);
            } else {
                getInfo(false, tag);
            }
        } else {
            error("You must hold an item in your main hand or look at block/entity.");
        }
    }

    private void getInfo(boolean isBlockState, Object data) {
        Text dataString = isBlockState ? formatBlockState(data) : NbtHelper.toPrettyPrintedText((NbtCompound) data);
        String dataType = isBlockState ? "STATE" : "NBT";

        MutableText copyButton = Text.literal(dataType);
        copyButton.setStyle(copyButton.getStyle()
            .withFormatting(Formatting.UNDERLINE)
            .withClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                this.toString("copy")
            ))
            .withHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                Text.literal("Copy the data to your clipboard.")
            )));

        MutableText text = Text.literal("");
        text.append(copyButton);

        if (data == null) text.append(" {}");
        else text.append(" ").append(dataString);

        info(text);
    }

    private void copyData(boolean isBlockState, NbtCompound tag, BlockState state) {
        String dataType = isBlockState ? "NBT + STATE" : "NBT";

        MutableText button = Text.literal("");
        if (isBlockState) {
            button.append(formatBlockState(state));
            button.append(NbtHelper.toPrettyPrintedText(tag));
        }

        String propertiesString = "";
        if (isBlockState) {
            String stateString = state.toString();
            Pattern pattern = Pattern.compile("\\[(.*?)\\]");
            Matcher matcher = pattern.matcher(stateString);
            propertiesString = matcher.find() ? matcher.group(1) : "";
        }

        mc.keyboard.setClipboard(isBlockState ? "[" + propertiesString + "]" + tag.toString() : tag.toString());
        MutableText builder = Text.literal(dataType);
        builder.setStyle(builder.getStyle()
            .withFormatting(Formatting.UNDERLINE)
            .withHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                isBlockState ? button : NbtHelper.toPrettyPrintedText(tag)
            )));

        MutableText text = Text.literal("");
        text.append(builder);
        text.append(Text.literal(" data copied!"));

        info(text);
    }

    private MutableText formatBlockState(Object state) {
        String stateString = state.toString();
        Pattern pattern = Pattern.compile("\\[(.*?)\\]");
        Matcher matcher = pattern.matcher(stateString);
        String propertiesString = matcher.find() ? matcher.group(1) : "";
        String[] properties = propertiesString.split(",");
        MutableText text = Text.literal("");

        text.append(Text.literal("["));

        for (String property : properties) {
            if (property.equals("")) {
                text.append(Text.literal("."));
                break;
            }

            String[] keyValue = property.split("=");

            MutableText keyText = Text.literal(keyValue[0])
                .formatted(Formatting.AQUA);
            MutableText valueText = Text.literal(keyValue[1])
                .formatted(Formatting.GOLD);

            text.append(keyText);
            text.append(Text.literal(" = "));
            text.append(valueText);
            text.append(Text.literal(", "));
        }

        text.getSiblings().remove(text.getSiblings().size() - 1);
        text.append(Text.literal("]"));

        return text;
    }

    private void setStack(ItemStack stack) {
        mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(36 + mc.player.getInventory().selectedSlot, stack));
    }

    private boolean validBasic(ItemStack stack) {
        if (!mc.player.getAbilities().creativeMode) {
            error("Creative mode only.");
            return false;
        }

        if (stack == null) {
            error("You must hold an item in your main hand.");
            return false;
        }
        return true;
    }
}

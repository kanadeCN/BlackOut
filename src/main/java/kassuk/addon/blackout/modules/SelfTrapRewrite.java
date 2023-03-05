package kassuk.addon.blackout.modules;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import kassuk.addon.blackout.enums.RotationType;
import kassuk.addon.blackout.enums.SwingState;
import kassuk.addon.blackout.enums.SwingType;
import kassuk.addon.blackout.managers.Managers;
import kassuk.addon.blackout.timers.BlockTimerList;
import kassuk.addon.blackout.utils.BOInvUtils;
import kassuk.addon.blackout.utils.OLEPOSSUtils;
import kassuk.addon.blackout.utils.PlaceData;
import kassuk.addon.blackout.utils.SettingUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/*
Ported from surround+
*/

public class SelfTrapRewrite extends BlackOutModule {
    public SelfTrapRewrite() {super(BlackOut.BLACKOUT, "SelfTrapRewrite", "KasumsSoft selftrap");}
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgToggle = settings.createGroup("Toggle");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final Setting<Boolean> pauseEat = sgGeneral.add(new BoolSetting.Builder()
        .name("Pause Eat")
        .description("Pauses when you are eating")
        .defaultValue(true)
        .build()
    );
    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("Switch Mode")
        .description(".")
        .defaultValue(SwitchMode.SilentBypass)
        .build()
    );
    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("Blocks")
        .description("Blocks to use.")
        .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.NETHERITE_BLOCK)
        .build()
    );
    private final Setting<Boolean> floor = sgGeneral.add(new BoolSetting.Builder()
        .name("Floor")
        .description("Places blocks under your feet.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> placeDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("Place Delay")
        .description("Delay between places.")
        .defaultValue(0)
        .range(0, 10)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Integer> places = sgGeneral.add(new IntSetting.Builder()
        .name("Places")
        .description("Blocks placed per place")
        .defaultValue(1)
        .range(1, 10)
        .sliderRange(1, 10)
        .build()
    );
    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("Delay")
        .description("Delay between placing at each spot.")
        .defaultValue(0.3)
        .range(0, 10)
        .sliderRange(0, 10)
        .build()
    );

    //  Toggle Page
    private final Setting<Boolean> toggleMove = sgToggle.add(new BoolSetting.Builder()
        .name("Toggle Move")
        .description("Toggles when you move horizontally")
        .defaultValue(true)
        .build()
    );
    private final Setting<ToggleYMode> toggleY = sgToggle.add(new EnumSetting.Builder<ToggleYMode>()
        .name("Toggle Y")
        .description("Toggles when you move vertically")
        .defaultValue(ToggleYMode.Full)
        .build()
    );
    private final Setting<Boolean> toggleSneak = sgToggle.add(new BoolSetting.Builder()
        .name("Toggle Sneak")
        .description("Toggles when you sneak")
        .defaultValue(false)
        .build()
    );

    //  Render Page
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("Shape Mode")
        .description(".")
        .defaultValue(ShapeMode.Both)
        .build()
    );
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("Line Color")
        .description("Color of the outlines")
        .defaultValue(new SettingColor(255, 0, 0, 150))
        .build()
    );
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("Side Color")
        .description(".")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );
    private final Setting<ShapeMode> supportShapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("Support Shape Mode")
        .description(".")
        .defaultValue(ShapeMode.Both)
        .build()
    );
    private final Setting<SettingColor> supportLineColor = sgRender.add(new ColorSetting.Builder()
        .name("Support Line Color")
        .description("Color of the outlines")
        .defaultValue(new SettingColor(255, 0, 0, 150))
        .build()
    );
    private final Setting<SettingColor> supportSideColor = sgRender.add(new ColorSetting.Builder()
        .name("Support Side Color")
        .description(".")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );
    public enum SwitchMode {
        Disabled,
        Normal,
        Silent,
        SilentBypass
    }
    public enum ToggleYMode {
        Disabled,
        Up,
        Down,
        Full
    }
    public enum TrapMode {
        Top,
        Face,
        Both
    }
    BlockTimerList timers = new BlockTimerList();
    BlockPos startPos = null;
    double placeTimer = 0;
    int placesLeft = 0;
    List<Render> render = new ArrayList<>();
    boolean lastSneak = false;

    @Override
    public void onActivate() {
        super.onActivate();
        if (mc.player == null || mc.world == null) {toggle();}
        startPos = mc.player.getBlockPos();
        placesLeft = places.get();
        placeTimer = 0;
        render = new ArrayList<>();
    }

    @EventHandler
    private void onRender(Render3DEvent event){
        placeTimer = Math.min(placeDelay.get(), placeTimer + event.frameTime);
        if (placeTimer >= placeDelay.get()) {
            placesLeft = places.get();
            placeTimer = 0;
        }
        render.forEach(item -> event.renderer.box(OLEPOSSUtils.getBox(item.pos), item.support ? supportSideColor.get() : sideColor.get(), item.support ? supportLineColor.get() : lineColor.get(), item.support ? supportShapeMode.get() : shapeMode.get(), 0));
        update();
    }

    void update() {
        if (mc.player != null && mc.world != null) {

            // Move Check
            if (toggleMove.get() && (mc.player.getBlockPos().getX() != startPos.getX() || mc.player.getBlockPos().getZ() != startPos.getZ())) {
                sendDisableMsg("moved");
                toggle();
                return;
            }

            // Y Check
            switch (toggleY.get()) {
                case Full -> {
                    if (mc.player.getBlockPos().getY() != startPos.getY()) {
                        sendDisableMsg("moved vertically");
                        toggle();
                        return;
                    }
                }
                case Up -> {
                    if (mc.player.getBlockPos().getY() > startPos.getY()) {
                        sendDisableMsg("moved up");
                        toggle();
                        return;
                    }
                }
                case Down -> {
                    if (mc.player.getBlockPos().getY() < startPos.getY()) {
                        sendDisableMsg("moved down");
                        toggle();
                        return;
                    }
                }
            }

            // Sneak Check
            if (toggleSneak.get()) {
                boolean isClicked = mc.options.sneakKey.isPressed();
                if (isClicked && !lastSneak) {
                    sendDisableMsg("sneaked");
                    toggle();
                    return;
                }
                lastSneak = isClicked;
            }

            List<BlockPos> placements = check();

            FindItemResult hotbar = InvUtils.findInHotbar(item -> item.getItem() instanceof BlockItem && blocks.get().contains(((BlockItem) item.getItem()).getBlock()));
            FindItemResult inventory = InvUtils.find(item -> item.getItem() instanceof BlockItem && blocks.get().contains(((BlockItem) item.getItem()).getBlock()));
            Hand hand = isValid(Managers.HOLDING.getStack()) ? Hand.MAIN_HAND : isValid(mc.player.getOffHandStack()) ? Hand.OFF_HAND : null;

            if ((hand != null || (switchMode.get() == SwitchMode.SilentBypass && inventory.slot() >= 0) || ((switchMode.get() == SwitchMode.Silent ||switchMode.get() == SwitchMode.Normal) && hotbar.slot() >= 0)) &&
                (!pauseEat.get() || !mc.player.isUsingItem()) && placesLeft > 0 && !placements.isEmpty()) {


                Map<PlaceData, BlockPos> toPlace = new HashMap<>();
                for (BlockPos placement : placements) {
                    PlaceData data = SettingUtils.getPlaceData(placement);
                    if (toPlace.size() < placesLeft && data.valid()) {
                        toPlace.put(data, placement);
                    }
                }
                sort(toPlace);

                if (!toPlace.isEmpty()) {
                    int obsidian = hand == Hand.MAIN_HAND ? Managers.HOLDING.getStack().getCount() :
                        hand == Hand.OFF_HAND ? mc.player.getOffHandStack().getCount() : -1;

                    switch (switchMode.get()) {
                        case Silent, Normal -> {
                            obsidian = hotbar.count();
                        }
                        case SilentBypass -> {
                            obsidian = inventory.slot() >= 0 ? inventory.count() : -1;
                        }
                    }

                    if (obsidian >= 0) {
                        boolean switched = false;
                        int i = 0;
                        for (Map.Entry<PlaceData, BlockPos> entry : toPlace.entrySet()) {
                            if (i >= Math.min(obsidian, toPlace.size())) {continue;}

                            PlaceData placeData = entry.getKey();
                            BlockPos pos = entry.getValue();
                            if (placeData.valid()) {
                                boolean rotated = !SettingUtils.shouldRotate(RotationType.Placing) || Managers.ROTATION.start(placeData.pos(), 1, RotationType.Placing);

                                if (!rotated) {
                                    break;
                                }
                                if (!switched) {
                                    if (hand == null) {
                                        switch (switchMode.get()) {
                                            case Silent, Normal -> {
                                                InvUtils.swap(hotbar.slot(), true);
                                                switched = true;
                                            }
                                            case SilentBypass -> {
                                                switched = BOInvUtils.invSwitch(inventory.slot());
                                            }
                                        }
                                    }
                                }
                                place(placeData, pos);
                            }
                        }

                        if (switched) {
                            switch (switchMode.get()) {
                                case Silent -> {
                                    InvUtils.swapBack();
                                }
                                case SilentBypass -> {
                                    BOInvUtils.swapBack();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    void place(PlaceData d, BlockPos ogPos) {
        timers.add(ogPos, delay.get());
        placeTimer = 0;
        placesLeft--;

        SettingUtils.swing(SwingState.Pre, SwingType.Placing);

        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,
            new BlockHitResult(new Vec3d(d.pos().getX() + 0.5, d.pos().getY() + 0.5, d.pos().getZ() + 0.5),
                d.dir(), d.pos(), false), 0));

        SettingUtils.swing(SwingState.Post, SwingType.Placing);

        if (SettingUtils.shouldRotate(RotationType.Placing)) {
            Managers.ROTATION.end(d.pos());
        }
    }

    boolean isValid(ItemStack item) {
        return item.getItem() instanceof BlockItem && blocks.get().contains(((BlockItem) item.getItem()).getBlock());
    }

    List<BlockPos> check() {
        List<BlockPos> list = new ArrayList<>();
        List<Render> renders = new ArrayList<>();
        List<BlockPos> blocks = getBlocks(getSize());
        if (mc.player != null && mc.world != null) {
            for (BlockPos position : blocks) {
                if (mc.world.getBlockState(position).getBlock().equals(Blocks.AIR)) {
                    if (!timers.contains(position) && !EntityUtils.intersectsWithEntity(OLEPOSSUtils.getBox(position), entity -> !entity.isSpectator() && entity.getType() != EntityType.ITEM)) {
                        PlaceData data = SettingUtils.getPlaceData(position);
                        if (data.valid()) {
                            list.add(position);
                        } else {
                            Direction best = null;
                            int value = -1;
                            double dist = Double.MAX_VALUE;
                            for (Direction dir : Direction.values()) {
                                if (mc.world.getBlockState(position.offset(dir)).getBlock() == Blocks.AIR) {
                                    PlaceData placeData = SettingUtils.getPlaceData(position.offset(dir));
                                    if (placeData.valid()) {
                                        if (!EntityUtils.intersectsWithEntity(OLEPOSSUtils.getBox(position.offset(dir)), entity -> !entity.isSpectator() && entity.getType() != EntityType.ITEM)) {
                                            double distance = OLEPOSSUtils.distance(OLEPOSSUtils.getMiddle(position.offset(dir)), mc.player.getPos());
                                            if (distance < dist || value <= 1) {
                                                dist = distance;
                                                best = dir;
                                                value = 2;
                                            }
                                        } else if (!EntityUtils.intersectsWithEntity(OLEPOSSUtils.getBox(position.offset(dir)), entity -> !entity.isSpectator() && entity.getType() != EntityType.ITEM && entity.getType() != EntityType.END_CRYSTAL)) {
                                            if (value <= 1) {
                                                double distance = OLEPOSSUtils.distance(OLEPOSSUtils.getMiddle(position.offset(dir)), mc.player.getPos());
                                                if (distance < dist || value <= 0) {
                                                    best = dir;
                                                    value = 1;
                                                    dist = distance;

                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (best != null) {
                                if (!timers.contains(position.offset(best))) {
                                    list.add(position.offset(best));
                                }
                                renders.add(new Render(position.offset(best), true));
                            }
                        }
                    }
                    renders.add(new Render(position, false));
                }
            }
        }
        render = renders;
        return list;
    }

    int[] getSize() {
        if (mc.player == null || mc.world == null) {return new int[]{0, 0, 0, 0};}

        Vec3d offset = mc.player.getPos().add(-mc.player.getBlockX(), -mc.player.getBlockY(), -mc.player.getBlockZ());
        return new int[]{offset.x < 0.3 ? -1 : 0, offset.x > 0.7 ? 1 : 0, offset.z < 0.3 ? -1 : 0, offset.z > 0.7 ? 1 : 0};
    }

    List<BlockPos> getBlocks(int[] size) {
        List<BlockPos> list = new ArrayList<>();
        if (mc.player != null && mc.world != null) {
            BlockPos pos = mc.player.getBlockPos().add(0, mc.player.getY() - Math.floor(mc.player.getY()) >= 0.2 ? 2 : 1, 0);
            for (int x = size[0] - 1; x <= size[1] + 1; x++) {
                for (int z = size[2] - 1; z <= size[3] + 1; z++) {
                    boolean isX = x == size[0] - 1 || x == size[1] + 1;
                    boolean isZ = z == size[2] - 1 || z == size[3] + 1;
                    boolean ignore = isX && !isZ ? !air(pos.add(OLEPOSSUtils.closerToZero(x), 0, z)) :
                        !isX && isZ && !air(pos.add(x, 0, OLEPOSSUtils.closerToZero(z)));
                    BlockPos bPos = null;
                    if (isX != isZ && !ignore) {
                        bPos = new BlockPos(x, pos.getY() ,z).add(pos.getX(), 0, pos.getZ());
                    } else if (!isX && !isZ && air(pos.add(x, 0, z))) {
                        bPos = new BlockPos(x, pos.getY() ,z).add(pos.getX(), 1, pos.getZ());
                    }
                    if (bPos != null && mc.world.getBlockState(bPos).getBlock().equals(Blocks.AIR)) {
                        list.add(bPos);
                    }
                }
            }
        }
        return list;
    }

    // Very shitty sorting
    void sort(Map<PlaceData, BlockPos> original) {
        Map<PlaceData, BlockPos> map = new HashMap<>();
        double lowest;
        PlaceData lData;
        BlockPos lPos;
        for (int i = 0; i < original.size(); i++) {
            lowest = Double.MAX_VALUE;
            lData = null;
            lPos = null;

            for (Map.Entry<PlaceData, BlockPos> entry : original.entrySet()) {
                double yaw = Rotations.getYaw(entry.getValue());
                if (yaw < lowest) {
                    lowest = yaw;
                    lData = entry.getKey();
                    lPos = entry.getValue();
                }
            }
            map.put(lData, lPos);
        }

        original.clear();
        original.putAll(map);
    }

    boolean air(BlockPos pos) {return mc.world.getBlockState(pos).getBlock().equals(Blocks.AIR);}

    record Render(BlockPos pos, boolean support) {}
}
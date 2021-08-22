package gregtech.common.metatileentities.storage;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Matrix4;
import com.google.common.collect.Lists;
import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.ClickButtonWidget;
import gregtech.api.gui.widgets.CycleButtonWidget;
import gregtech.api.gui.widgets.TextFieldWidget;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.render.Textures;
import gregtech.api.util.GTUtility;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.List;

public class MetaTileEntityCreativeEnergy extends MetaTileEntity implements IEnergyContainer {

    private long voltage = 0;
    private int amps = 1;

    private int setTier = 0;
    private boolean active = false;

    private final List<Character> ALLOWED_CHARS = Lists.newArrayList('0', '1', '2', '3', '4', '5', '6', '7', '8', '9');

    public MetaTileEntityCreativeEnergy() {
        super(new ResourceLocation(GTValues.MODID, "infinite_energy"));
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        IVertexOperation[] renderPipeline = ArrayUtils.add(pipeline, new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(getPaintingColorForRendering())));
        Textures.VOLTAGE_CASINGS[14].render(renderState, translation, renderPipeline, Cuboid6.full);
        for (EnumFacing face : EnumFacing.VALUES) {
            Textures.INFINITE_EMITTER_FACE.renderSided(face, renderState, translation, pipeline);
        }
    }

    @Override
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(Textures.VOLTAGE_CASINGS[this.setTier].getParticleSprite(), this.getPaintingColor());
    }

    @Override
    public MetaTileEntity createMetaTileEntity(MetaTileEntityHolder holder) {
        return new MetaTileEntityCreativeEnergy();
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER)
            return GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER.cast(this);
        return super.getCapability(capability, side);
    }

    @Override
    protected ModularUI createUI(EntityPlayer entityPlayer) {
        ModularUI.Builder builder = ModularUI.defaultBuilder()
                .widget(new CycleButtonWidget(7, 7, 30, 20, GTValues.VN, () -> setTier, tier -> {
                    setTier = tier;
                    if (tier > 0)
                        voltage = GTValues.V[setTier - 1];
                    else
                        voltage = 0;
                }));
        builder.label(7, 32, "Voltage");
        builder.widget(new TextFieldWidget(7, 44, 156, 20, true, () -> String.valueOf(voltage), value -> {
            if(!value.isEmpty()) {
                voltage = Long.parseLong(value);
                setTier = 0;
            }
        }).setValidator(value -> {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (!ALLOWED_CHARS.contains(c))
                    return false;
            }
            return true;
        }));
        builder.label(7, 74, "Amperage");
        builder.widget(new ClickButtonWidget(7, 87, 20, 20, "-", data -> amps = amps-- == -1 ? 0 : amps--));
        builder.widget(new TextFieldWidget(29, 87, 118, 20, true, () -> String.valueOf(amps), value -> {
            if(!value.isEmpty()) {
                amps = Integer.parseInt(value);
            }
        }).setValidator(value -> {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (!ALLOWED_CHARS.contains(c))
                    return false;
            }
            return true;
        }));
        builder.widget(new ClickButtonWidget(149, 87, 20, 20, "+", data -> amps++));

        builder.widget(new CycleButtonWidget(7, 139, 162, 20, () -> active, value -> active = value, "Not active", "Active"));

        return builder.build(getHolder(), entityPlayer);
    }

    @Override
    public void update() {
        super.update();
        if (!active || voltage <= 0 || amps <= 0) return;
        int ampsUsed = 0;
        for (EnumFacing facing : EnumFacing.values()) {
            EnumFacing opposite = facing.getOpposite();
            TileEntity tile = getWorld().getTileEntity(getPos().offset(facing));
            if (tile != null) {
                IEnergyContainer container = tile.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER, opposite);
                if (container == null || !container.inputsEnergy(opposite) || container.getEnergyCanBeInserted() == 0)
                    continue;
                ampsUsed += container.acceptEnergyFromNetwork(opposite, voltage, amps - ampsUsed);
                if (ampsUsed >= amps)
                    break;
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        data.setLong("Voltage", voltage);
        data.setInteger("Amps", amps);
        data.setByte("Tier", (byte) setTier);
        data.setBoolean("Active", active);
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        voltage = data.getLong("Voltage");
        amps = data.getInteger("Amps");
        setTier = data.getByte("Tier");
        active = data.getBoolean("Active");
        super.readFromNBT(data);
    }

    @Override
    public long acceptEnergyFromNetwork(EnumFacing side, long voltage, long amperage) {
        return 0;
    }

    @Override
    public boolean inputsEnergy(EnumFacing side) {
        return false;
    }

    @Override
    public boolean outputsEnergy(EnumFacing side) {
        return true;
    }

    @Override
    public long changeEnergy(long differenceAmount) {
        return 0;
    }

    @Override
    public long getEnergyStored() {
        return 69;
    }

    @Override
    public long getEnergyCapacity() {
        return 420;
    }

    @Override
    public long getInputAmperage() {
        return 0;
    }

    @Override
    public long getInputVoltage() {
        return 0;
    }

    @Override
    public long getOutputVoltage() {
        return voltage;
    }

    @Override
    public long getOutputAmperage() {
        return amps;
    }

}

package com.elytradev.infraredstone.tile;

import java.util.ArrayList;
import java.util.List;

import com.elytradev.infraredstone.InfraRedstone;
import com.elytradev.infraredstone.block.BlockBase;
import com.elytradev.infraredstone.block.BlockGateAnd;
import com.elytradev.infraredstone.block.ModBlocks;
import com.elytradev.infraredstone.api.IMultimeterProbe;
import com.elytradev.infraredstone.logic.InRedLogic;
import com.elytradev.infraredstone.logic.impl.InfraRedstoneHandler;

import com.elytradev.infraredstone.util.enums.EnumInactiveSelection;
import com.google.common.base.Predicates;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.capabilities.Capability;

public class TileEntityGateAnd extends TileEntityIRComponent implements ITickable, IMultimeterProbe {
    private InfraRedstoneHandler signal = new InfraRedstoneHandler();
    private int valLeft;
    private int valBack;
    private int valRight;
    public boolean booleanMode;
    public EnumInactiveSelection inactive = EnumInactiveSelection.NONE;

    //Transient data to throttle sync down here
    boolean lastActive = false;
    int lastValLeft = 0;
    int lastValBack = 0;
    int lastValRight = 0;
    boolean lastBooleanMode = false;
    EnumInactiveSelection lastInactive = EnumInactiveSelection.NONE;

    public void update() {
        if (world.isRemote || !hasWorld()) return;

        IBlockState state = world.getBlockState(this.getPos());

        if (InRedLogic.isIRTick()) {
            //IR tick means we're searching for a next value
            if (state.getBlock() instanceof BlockGateAnd) {
                EnumFacing left = state.getValue(BlockGateAnd.FACING).rotateYCCW();
                EnumFacing right = state.getValue(BlockGateAnd.FACING).rotateY();
                EnumFacing back = state.getValue(BlockGateAnd.FACING).getOpposite();
                int sigLeft = InRedLogic.findIRValue(world, pos, left);
                int sigRight = InRedLogic.findIRValue(world, pos, right);
                int sigBack = InRedLogic.findIRValue(world, pos, back);
                List<Integer> signals = new ArrayList<>();

                valLeft = sigLeft;
                valRight = sigRight;
                valBack = sigBack;
                int result = 0b11_1111; //63

                if (!booleanMode) {
                    switch (inactive) {
                        case LEFT:
                            signals.add(sigBack);
                            signals.add(sigRight);
                            break;
                        case BACK:
                            signals.add(sigLeft);
                            signals.add(sigRight);
                            break;
                        case RIGHT:
                            signals.add(sigLeft);
                            signals.add(sigBack);
                            break;
                        case NONE:
                            signals.add(sigLeft);
                            signals.add(sigBack);
                            signals.add(sigRight);
                    }

                    for (int signal : signals) {
                        // if any input added to signal is 0b00_0000, will result in no output
                        result &= signal;
                    }
                } else {
                    switch (inactive) {
                        case LEFT:
                            result = (sigBack > 0 && sigRight > 0)? 1 : 0;
                            break;
                        case BACK:
                            result = (sigLeft > 0 && sigRight > 0)? 1 : 0;
                            break;
                        case RIGHT:
                            result = (sigLeft > 0 && sigBack > 0)? 1 : 0;
                            break;
                        case NONE:
                            result = (sigLeft > 0 && sigBack > 0 && sigRight > 0)? 1 : 0;
                    }
                }
                
                signal.setNextSignalValue(result);
                markDirty();
            }

        } else {
            //Not an IR tick, so this is a "copy" tick. Adopt the previous tick's "next" value.
            signal.setSignalValue(signal.getNextSignalValue());
            markDirty();
        }
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if (facing == EnumFacing.UP || facing == EnumFacing.DOWN) return false;
        if (capability==InfraRedstone.CAPABILITY_IR) {
            if (world==null) return true;
            if (facing==null) return true;
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock()==ModBlocks.GATE_AND) {
                EnumFacing gateAndFront = state.getValue(BlockGateAnd.FACING);
                if (gateAndFront==facing) {
                    return true;
                } else if (gateAndFront==facing.rotateYCCW()) {
                    return true;
                } else if (gateAndFront==facing.getOpposite()) {
                    return true;
                } else if (gateAndFront==facing.rotateY()) {
                    return true;
                } else {
                    return false;
                }
            }

            return false;
        }

        return super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability==InfraRedstone.CAPABILITY_IR) {
            if (world==null) return (T)InfraRedstoneHandler.ALWAYS_OFF;
            if (facing==null) return (T) signal;

            IBlockState state = world.getBlockState(pos);
            if (state.getBlock()==ModBlocks.GATE_AND) {
                EnumFacing gateAndFront = state.getValue(BlockGateAnd.FACING);
                if (gateAndFront==facing) {
                    return (T) signal;
                } else if (gateAndFront==facing.rotateYCCW()) {
                    return (T) InfraRedstoneHandler.ALWAYS_OFF;
                } else if (gateAndFront==facing.getOpposite()) {
                    return (T) InfraRedstoneHandler.ALWAYS_OFF;
                } else if (gateAndFront==facing.rotateY()) {
                    return (T)InfraRedstoneHandler.ALWAYS_OFF;
                } else {
                    return null;
                }
            }
            return (T)InfraRedstoneHandler.ALWAYS_OFF; //We can't tell what our front face is, so supply a dummy that's always-off.
        }

        return super.getCapability(capability, facing);
    }

    public void toggleBooleanMode() {
        if (booleanMode) {
            booleanMode = false;
            world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 0.3f, 0.5f);
        } else {
            booleanMode = true;
            world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 0.3f, 0.55f);
        }
        markDirty();
    }

    public void toggleInactive(EnumInactiveSelection newInactive) {
        if (inactive == newInactive) {
            inactive = EnumInactiveSelection.NONE;
        } else {
            inactive = newInactive;
        }
        world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 0.3f, 0.45f);
        markDirty();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagCompound tag = super.writeToNBT(compound);
        tag.setTag("Signal", InfraRedstone.CAPABILITY_IR.writeNBT(signal, null));
        //please forgive me, falk. We'll work on moving these out soon.
        tag.setInteger("Left", valLeft);
        tag.setInteger("Back", valBack);
        tag.setInteger("Right", valRight);
        tag.setBoolean("BooleanMode", booleanMode);
        tag.setString("Inactive", inactive.getName());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("Signal")) InfraRedstone.CAPABILITY_IR.readNBT(signal, null, compound.getTag("Signal"));
        valLeft = compound.getInteger("Left");
        valBack = compound.getInteger("Back");
        valRight = compound.getInteger("Right");
        booleanMode = compound.getBoolean("BooleanMode");
        inactive = EnumInactiveSelection.forName(compound.getString("Inactive"));
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        readFromNBT(tag);
        if (lastBooleanMode!=booleanMode || lastInactive!= inactive) {
            world.markBlockRangeForRenderUpdate(pos, pos);
            lastBooleanMode = booleanMode;
            lastInactive = inactive;
        }
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }

    @Override
    public void markDirty() {
        super.markDirty();
        // again, I've copy-pasted this like 12 times, should probably go into Concrete
        if (!hasWorld() || getWorld().isRemote) return;
        boolean active = isActive();
        if (active!=lastActive
                || booleanMode!=lastBooleanMode
                || inactive!=lastInactive
                || valLeft!=lastValLeft
                || valRight!=lastValRight
                || valBack!=lastValBack) { //Throttle updates - only send when something important changes

            WorldServer ws = (WorldServer)getWorld();
            Chunk c = getWorld().getChunk(getPos());
            SPacketUpdateTileEntity packet = new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
            for (EntityPlayerMP player : getWorld().getPlayers(EntityPlayerMP.class, Predicates.alwaysTrue())) {
                if (ws.getPlayerChunkMap().isPlayerWatchingChunk(player, c.x, c.z)) {
                    player.connection.sendPacket(packet);
                }
            }

            if (booleanMode!=lastBooleanMode || inactive!=lastInactive) {
                //IBlockState state = world.getBlockState(pos);
                //ws.markAndNotifyBlock(pos, c, state, state, 1 | 2 | 16);
            } else if (isActive()!=lastActive
                    || valLeft!=lastValLeft
                    || valRight!=lastValRight
                    || valBack!=lastValBack) {
                //BlockState isn't changing, but we need to notify the block in front of us so that vanilla redstone updates
                IBlockState state = world.getBlockState(pos);
                if (state.getBlock()==ModBlocks.GATE_AND) {
                    EnumFacing facing = state.getValue(BlockGateAnd.FACING);
                    BlockPos targetPos = pos.offset(facing);
                    IBlockState targetState = world.getBlockState(targetPos);
                    if (!(targetState.getBlock() instanceof BlockBase)) {
                        //Not one of ours. Update its redstone, and let observers see the fact that we updated too
                        world.markAndNotifyBlock(pos, world.getChunk(pos), state, state, 1);
                        world.markAndNotifyBlock(targetPos, world.getChunk(targetPos), targetState, targetState, 3); // 1 : Just cuase a BUD and notify observers
                    }
                }
            }

            lastInactive = inactive;
            lastBooleanMode = booleanMode;
            lastActive = active;
            lastValLeft = valLeft;
            lastValRight = valRight;
            lastValBack = valBack;
        }
    }

    public boolean isActive() {
        return signal.getSignalValue()!=0;
    }
    public boolean isLeftActive() {
        return valLeft!=0;
    }
    public boolean isBackActive() {
        return valBack!=0;
    }
    public boolean isRightActive() {
        return valRight!=0;
    }

    @Override
    public TextComponentString getProbeMessage() {
        TextComponentTranslation i18n = new TextComponentTranslation("msg.inred.multimeter.out");
        return new TextComponentString(i18n.getFormattedText()+getValue());
    }

    private String getValue() {
        int signal = this.signal.getSignalValue();
        int bit1 = ((signal & 0b00_0001) != 0) ? 1:0;
        int bit2 = ((signal & 0b00_0010) != 0) ? 1:0;
        int bit3 = ((signal & 0b00_0100) != 0) ? 1:0;
        int bit4 = ((signal & 0b00_1000) != 0) ? 1:0;
        int bit5 = ((signal & 0b01_0000) != 0) ? 1:0;
        int bit6 = ((signal & 0b10_0000) != 0) ? 1:0;
        return ": 0b"+bit6+bit5+"_"+bit4+bit3+bit2+bit1+" ("+signal+")";
    }
}

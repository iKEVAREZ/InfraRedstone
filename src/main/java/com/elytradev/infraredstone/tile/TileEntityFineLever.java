package com.elytradev.infraredstone.tile;

import com.elytradev.infraredstone.logic.IInfraRedstone;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TileEntityFineLever extends TileEntityIRComponent implements IInfraRedstone {
    public boolean active = true;
    private int signal = 63;

    public void toggleState() {
        if (active) {
            active = false;
            signal = 0;
        } else {
            active = true;
            signal = 63;
        }
    }

    @Override
    public int getSignalValue(World world, BlockPos pos, IBlockState state, EnumFacing inspectingFrom) {
        return signal;
    }

}

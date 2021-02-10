package me.botsko.prism.api;

import org.bukkit.block.BlockState;

/**
 * Created for use Prism
 * Created by Narimm on 11/01/2021.
 */
public interface BlockStateChange {

    BlockState getOriginalBlock();
    BlockState getNewBlock();
}

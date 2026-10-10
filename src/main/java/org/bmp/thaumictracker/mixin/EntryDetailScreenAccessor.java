package org.bmp.thaumictracker.mixin;

import com.leclowndu93150.thaumaturge.client.screen.research.EntryDetailScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Only exposes the selected entry; research progression remains owned by Thaumaturge. */
@Mixin(EntryDetailScreen.class)
public interface EntryDetailScreenAccessor {
    @Accessor("entryId")
    Identifier thaumictracker$entryId();
}

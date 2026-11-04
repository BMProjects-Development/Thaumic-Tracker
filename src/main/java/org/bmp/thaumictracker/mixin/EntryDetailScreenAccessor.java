package org.bmp.thaumictracker.mixin;

import com.leclowndu93150.thaumaturge.client.screen.research.EntryDetailScreen;
import com.leclowndu93150.thaumaturge.client.screen.research.detail.EntryDetailModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Only exposes the book model; research progression remains owned by Thaumaturge. */
@Mixin(EntryDetailScreen.class)
public interface EntryDetailScreenAccessor {
    @Accessor("model")
    EntryDetailModel thaumictracker$model();
}

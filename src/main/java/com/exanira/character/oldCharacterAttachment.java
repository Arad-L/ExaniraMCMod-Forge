package com.exanira.character;

import com.exanira.ExaniraMod;
import com.exanira.event.PendingEventAttachment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class CharacterAttachment {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ExaniraMod.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CharacterSheet>> CHARACTER_SHEET =
            ATTACHMENT_TYPES.register("character_sheet", () ->
                    AttachmentType.serializable(CharacterSheet::new).build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PendingEventAttachment>> PENDING_EVENT =
            ATTACHMENT_TYPES.register("pending_event", () ->
                    AttachmentType.serializable(PendingEventAttachment::new).build()
            );
}

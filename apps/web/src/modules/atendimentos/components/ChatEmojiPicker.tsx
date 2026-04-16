"use client";

import dynamic from "next/dynamic";
import { EmojiStyle, SuggestionMode, Theme, type EmojiClickData } from "emoji-picker-react";

const EmojiPicker = dynamic(() => import("emoji-picker-react"), {
    ssr: false,
    loading: () => (
        <div className="flex h-[24.5rem] w-[21.5rem] items-center justify-center text-sm text-black/55">
            Carregando emojis...
        </div>
    ),
});

type ChatEmojiPickerProps = {
    onSelect: (emoji: string) => void;
};

export function ChatEmojiPicker({ onSelect }: ChatEmojiPickerProps) {
    return (
        <div className="absolute bottom-12 left-0 z-30 overflow-hidden rounded-2xl border border-black/10 bg-white shadow-lg">
            <EmojiPicker
                onEmojiClick={(emojiData: EmojiClickData) => onSelect(emojiData.emoji)}
                autoFocusSearch={false}
                emojiStyle={EmojiStyle.NATIVE}
                height={392}
                lazyLoadEmojis
                previewConfig={{ showPreview: false }}
                searchPlaceHolder="Buscar emoji"
                suggestedEmojisMode={SuggestionMode.RECENT}
                theme={Theme.LIGHT}
                width={344}
            />
        </div>
    );
}

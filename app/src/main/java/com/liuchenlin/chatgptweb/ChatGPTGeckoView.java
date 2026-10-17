package com.liuchenlin.chatgptweb;

import android.content.Context;
import org.mozilla.geckoview.GeckoView;

/** Thin GeckoView shell. Keyboard, IME and text-editing semantics belong entirely to Gecko/Web. */
public final class ChatGPTGeckoView extends GeckoView {
    public ChatGPTGeckoView(Context context) {
        super(context);
    }
}

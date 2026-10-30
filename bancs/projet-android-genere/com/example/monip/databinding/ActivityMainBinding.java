package com.example.monip.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ViewDataBinding;
import com.example.monip.Greeter;
import com.example.monip.R;

/**
 * Simulacre du ActivityMainBinding généré par le compilateur data binding
 * (banc projet Android réel) — mêmes champs publics, mêmes méthodes
 * statiques inflate/bind que la génération réelle d'AGP 8.4.
 */
public abstract class ActivityMainBinding extends ViewDataBinding {

    @NonNull
    public final LinearLayout main;

    @NonNull
    public final TextView textTitre;

    @NonNull
    public final Greeter liaison;

    protected ActivityMainBinding(
            @Nullable Object _bindingComponent,
            @NonNull View _root,
            int _localFieldCount,
            @NonNull LinearLayout main,
            @NonNull TextView textTitre,
            @NonNull Greeter liaison) {
        super(_bindingComponent, _root, _localFieldCount);
        this.main = main;
        this.textTitre = textTitre;
        this.liaison = liaison;
    }

    @NonNull
    public static ActivityMainBinding inflate(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup viewGroup, boolean attachToParent) {
        throw new UnsupportedOperationException("simulacre de banc");
    }

    @NonNull
    public static ActivityMainBinding inflate(@NonNull LayoutInflater inflater) {
        return inflate(inflater, null, false);
    }

    @NonNull
    public static ActivityMainBinding bind(@NonNull View rootView) {
        throw new UnsupportedOperationException("simulacre de banc");
    }
}

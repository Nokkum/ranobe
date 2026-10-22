package org.ranobe.ranobe.ui.search;

import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.ranobe.ranobe.R;
import org.ranobe.ranobe.util.OptionPicks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// One of the option chips under the search bar: All Sources, All Chapters or Any Status. The chip is on to
// begin with. Switching it off opens out the choices below the row, and several of them can be picked. Picking
// none of them, or every one of them, is the same as "all": the chip switches back on, the picks clear and the
// choices close. Switching the chip on again clears the picks too.
final class OptionSection {
    interface Listener {
        // The picks changed; an empty set means "all".
        void onSelectionChanged(Set<String> selected);

        // The choices were opened out or closed.
        void onOpenChanged(boolean open);
    }

    private final Chip allChip;
    private final ChipGroup options;
    private final Listener listener;
    private final Map<String, Chip> chips = new LinkedHashMap<>();
    // while the section is being set from outside, a chip changing isn't the person picking
    private boolean updating = false;

    OptionSection(Chip allChip, ChipGroup options, Listener listener) {
        this.allChip = allChip;
        this.options = options;
        this.listener = listener;
        // a chip toggles itself before its click listener runs, so isChecked() is already the new state
        allChip.setOnClickListener(v -> onAllClicked());
    }

    // Fills the choices: an id for each (what the section reports back) and the label the chip shows.
    void setOptions(LayoutInflater inflater, List<String> ids, List<String> labels) {
        options.removeAllViews();
        chips.clear();
        for (int i = 0; i < ids.size(); i++) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_source_chip, options, false);
            chip.setText(labels.get(i));
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (!updating) onOptionToggled();
            });
            options.addView(chip);
            chips.put(ids.get(i), chip);
        }
    }

    // Shows a state without reporting it as a change. The choices are open when `open`, or when any is picked.
    // Picks that cover every choice (say, a source was switched off since) show as "all".
    void show(Set<String> picked, boolean open) {
        if (!picked.isEmpty() && OptionPicks.isAll(picked, chips.keySet())) {
            picked = Collections.emptySet();
            open = false;
        }
        updating = true;
        for (Map.Entry<String, Chip> entry : chips.entrySet()) {
            entry.getValue().setChecked(picked.contains(entry.getKey()));
        }
        boolean expanded = open || !picked.isEmpty();
        allChip.setChecked(!expanded);
        options.setVisibility(expanded ? View.VISIBLE : View.GONE);
        updating = false;
    }

    // The ids picked, in the order the choices are listed.
    Set<String> selected() {
        Set<String> picked = new LinkedHashSet<>();
        for (Map.Entry<String, Chip> entry : chips.entrySet()) {
            if (entry.getValue().isChecked()) picked.add(entry.getKey());
        }
        return picked;
    }

    private void onAllClicked() {
        if (allChip.isChecked()) {
            // back to "all": the picks go and the choices close
            updating = true;
            for (Chip chip : chips.values()) chip.setChecked(false);
            updating = false;
            options.setVisibility(View.GONE);
            listener.onOpenChanged(false);
            listener.onSelectionChanged(Collections.<String>emptySet());
        } else {
            // off: the choices open out. Nothing is picked yet, so what is searched doesn't change.
            options.setVisibility(View.VISIBLE);
            listener.onOpenChanged(true);
        }
    }

    private void onOptionToggled() {
        Set<String> picked = selected();
        if (OptionPicks.isAll(picked, chips.keySet())) {
            // Nothing is narrowed any more: the last pick was undone, or every choice has been picked. Either
            // way that is "all", so the chip goes back on and the choices close. The full set is never
            // reported, so there is no search for it.
            updating = true;
            for (Chip chip : chips.values()) chip.setChecked(false);
            updating = false;
            allChip.setChecked(true);
            options.setVisibility(View.GONE);
            listener.onOpenChanged(false);
            listener.onSelectionChanged(Collections.<String>emptySet());
        } else {
            allChip.setChecked(false);
            listener.onSelectionChanged(picked);
        }
    }
}

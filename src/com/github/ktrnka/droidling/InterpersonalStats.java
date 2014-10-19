
package com.github.ktrnka.droidling;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import android.content.Context;

/**
 * Wrapper around the stats from InterpersonalActivity to load/save.
 */
public class InterpersonalStats {
    ArrayList<Item> list;

    public InterpersonalStats() {
        list = new ArrayList<Item>();
    }

    public InterpersonalStats(FileInputStream in) throws IOException {
        readFrom(in);
    }

    public void cacheStrings(Context context) {
        for (Item item : list)
            item.details.buildFormattedString(context);
    }

    private void readFrom(FileInputStream in) throws IOException {
        DataInputStream dataIn = new DataInputStream(new BufferedInputStream(in));

        int numItems = dataIn.readInt();
        int version = dataIn.readInt();
        if (version != InterpersonalSingleStats.seralizationVersion)
            throw new IOException("Stored file doesn't match serialization version.");

        list = new ArrayList<Item>(numItems);

        for (int i = 0; i < numItems; i++) {
            add(dataIn.readUTF(), InterpersonalSingleStats.deserialize(dataIn));
        }

        dataIn.close();
    }

    public void writeTo(FileOutputStream out) throws IOException {
        DataOutputStream dataOut = new DataOutputStream(new BufferedOutputStream(out));

        dataOut.writeInt(list.size());
        dataOut.writeInt(InterpersonalSingleStats.seralizationVersion);

        for (Item item : list) {
            dataOut.writeUTF(item.name.toString());
            item.details.serialize(dataOut);
        }

        dataOut.close();
    }

    public void add(CharSequence name, InterpersonalSingleStats details) {
        list.add(new Item(name, details));
    }

    public static class Item {
        CharSequence name;
        InterpersonalSingleStats details;

        public Item(CharSequence name, InterpersonalSingleStats details) {
            this.name = name;
            this.details = details;
        }
    }
}

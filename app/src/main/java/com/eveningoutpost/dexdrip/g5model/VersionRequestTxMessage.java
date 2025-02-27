package com.eveningoutpost.dexdrip.g5model;

import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.Models.UserError;

/**
 * Created by jamorham on 25/11/2016.
 */

public class VersionRequestTxMessage extends BaseMessage {

    static final byte opcode0 = 0x20;
    static final byte opcode1 = 0x4A;
    static final byte opcode2 = 0x52;

    static final byte INFO_2 = 2;

    public VersionRequestTxMessage() {
        this(0);
    }

    public VersionRequestTxMessage(final int version) {
        byte this_opcode = 0;
        int length = 3;
        switch (version) {
            case 0:
                this_opcode = opcode0;
                break;
            case 1:
                this_opcode = opcode1;
                break;
            case 2:
                this_opcode = opcode2;
                break;
            case 3:
                this_opcode = opcode2;
                length = 4;
                break;

        }
        init(this_opcode, length);

        if (version == 3) {
            data.put(INFO_2);
            appendCRC();
        }

        UserError.Log.d(TAG, "VersionTx (" + version + ") dbg: " + JoH.bytesToHex(byteSequence));
    }
}


package io.github.alyphen.amethyst.common.packet.control;

import io.github.alyphen.amethyst.common.control.Control;
import io.github.alyphen.amethyst.common.packet.Packet;

public class PacketControlReleased extends Packet {

    private String control;

    public PacketControlReleased(Control control) {
        this.control = control.name();
    }

    public Control getControl() {
        return Control.valueOf(control);
    }

}

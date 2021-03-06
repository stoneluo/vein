package com.vein.transport.netty;

import com.vein.serializer.api.Serializable;
import com.vein.serializer.api.AbstractSerializableManager;
import com.vein.transport.api.Request;
import com.vein.transport.api.Response;

/**
 * @author shifeng.luo
 * @version created on 2017/9/11 下午5:40
 */
public class MessageTypes extends AbstractSerializableManager {

    public MessageTypes() {
        SerializableType[] types = SerializableType.values();
        for (SerializableType type : types) {
            register(type.code, type.clazz);
        }
    }


    public enum SerializableType {
        REQUEST((byte) 1, Request.class),
        RESPONSE((byte) 2, Response.class);


        public byte code;
        public Class<? extends Serializable> clazz;

        SerializableType(byte code, Class<? extends Serializable> clazz) {
            this.code = code;
            this.clazz = clazz;
        }
    }
}

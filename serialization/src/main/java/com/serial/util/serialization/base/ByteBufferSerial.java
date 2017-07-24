package com.serial.util.serialization.base;

import com.serial.util.internal.InternalSerialUtils;
import com.serial.util.internal.Pools;
import com.serial.util.serialization.SerializationContext;
import com.serial.util.serialization.serializer.Serializer;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class ByteBufferSerial implements Serial {
    @Nullable
    private final Pools.SynchronizedPool<byte[]> mBufferPool;
    @NotNull
    private final SerializationContext mContext;

    public ByteBufferSerial(int bufferCount, int bufferSize) {
        this(createPool(bufferCount, bufferSize));
    }

    public ByteBufferSerial() {
        this(SerializationContext.ALWAYS_RELEASE, null);
    }

    public ByteBufferSerial(@NotNull SerializationContext context) {
        this(context, null);
    }

    public ByteBufferSerial(@Nullable Pools.SynchronizedPool<byte[]> pool) {
        this(SerializationContext.ALWAYS_RELEASE, pool);
    }

    public ByteBufferSerial(@NotNull SerializationContext context,
            @Nullable Pools.SynchronizedPool<byte[]> pool) {
        mBufferPool = pool;
        mContext = context;
    }

    @Override
    @NotNull
    public <T> byte[] toByteArray(@Nullable T value, @NotNull Serializer<T> serializer)
            throws IOException {
        if (value == null) {
            return InternalSerialUtils.EMPTY_BYTE_ARRAY;
        }
        final Pools.SynchronizedPool<byte[]> currentPool = mBufferPool;
        final byte[] tempBuffer = currentPool != null ? currentPool.acquire() : null;
        if (tempBuffer != null) {
            try {
                synchronized (tempBuffer) {
                    return toByteArray(value, serializer, tempBuffer);
                }
            } finally {
                currentPool.release(tempBuffer);
            }
        }
        return toByteArray(value, serializer, null);
    }

    @NotNull
    public <T> byte[] toByteArray(@Nullable T value, @NotNull Serializer<T> serializer,
            @Nullable byte[] tempBuffer) throws IOException {
        if (value == null) {
            return InternalSerialUtils.EMPTY_BYTE_ARRAY;
        }
        final ByteBufferSerializerOutput serializerOutput = new ByteBufferSerializerOutput(tempBuffer);
        try {
            serializer.serialize(mContext, serializerOutput, value);
        } catch (IOException e) {
            throw e;
        }
        return serializerOutput.getSerializedData();
    }

    @Override
    @Nullable
    @Contract("null, _ -> null")
    public <T> T fromByteArray(@Nullable byte[] bytes, @NotNull Serializer<T> serializer)
            throws IOException,
            ClassNotFoundException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        final SerializerInput serializerInput = new ByteBufferSerializerInput(bytes);
        try {
            return serializer.deserialize(mContext, serializerInput);
        } catch (IOException | ClassNotFoundException | IllegalStateException e) {
            throw e;
        }
    }

    @NotNull
    private static Pools.SynchronizedPool<byte[]> createPool(int bufferCount, int bufferSize) {
        final Pools.SynchronizedPool<byte[]> pool = new Pools.SynchronizedPool<>(bufferCount);
        for (int i = 0; i < bufferCount; i++) {
            pool.release(new byte[bufferSize]);
        }
        return pool;
    }
}
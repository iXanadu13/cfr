package com.github.ixanadu13;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

import java.io.IOException;

public final class ClassUtil {
    public static int getClassVersion(int opcode) {
        if (opcode == Opcodes.V1_1) return 1;
        else {
            return opcode - 44;
        }
    }

    /**
     * 根据运行时环境获取类
     * @param name 类内部名
     * @return ClassReader
     */
    public static ClassReader fromRuntime(String name) {
        try {
            return new ClassReader(name);
        } catch(IOException e) {
            // Expected / allowed: ignore these
        } catch(Exception ex) {
            // Unexpected
            throw new IllegalStateException("Failed to load class from runtime: " + name, ex);
        }
        return null;
    }
}

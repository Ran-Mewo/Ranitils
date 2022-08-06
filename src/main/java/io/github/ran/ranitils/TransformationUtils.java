/*
 * This file is part of Ranitils, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 Ran
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.ran.ranitils;

import javassist.*;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

// TODO: Javadocs
@SuppressWarnings("unused")
public class TransformationUtils {

    private String pathToJAR;
    private String pathToClassInsideJAR;
    private CtClass cc;
    private boolean loaded = false;
    private ClassPool pool;

    protected void setPathToJAR(String pathToJAR) {
        this.pathToJAR = pathToJAR;
    }
    
    protected void setPool(ClassPool pool) {
        this.pool = pool;
    }
    
    protected ClassPool getPool() {
        if (pool == null) pool = ClassPool.getDefault();
        return pool;
    }

    protected CtClass setClassToWorkWith(CtClass cc) {
        return this.cc = cc;
    }

    protected CtClass makeClass(String className, boolean isInterface, @Nullable String... imports) {
        loaded = true;
        className = className.replace("\\", ".").replace("/", ".");
        if (className.endsWith(".class")) className = className.substring(0, className.length() - 6);
        if (pool == null) pool = ClassPool.getDefault();
        if (imports != null) {
            for (String imp : imports) {
                pool.importPackage(imp);
            }
        }
        if (isInterface) return cc = pool.makeInterface(className);
        return cc = pool.makeClass(className);
    }

    protected void makeInitializer() throws CannotCompileException {
        cc.makeClassInitializer();
    }

    protected void makeConstructor(String constructor) throws CannotCompileException {
        CtConstructor ctConstructor = CtNewConstructor.make(constructor, cc);
        cc.addConstructor(ctConstructor);
    }

    protected void makeConstructorsPublic() {
        for (CtConstructor ctConstructor : cc.getConstructors()) {
            ctConstructor.setModifiers(Modifier.setPublic(ctConstructor.getModifiers()));
        }
    }

    protected int makeMutable(int mod) {
        return (mod & ~(Modifier.FINAL));
    }

    protected int getModifiers() {
        return cc.getModifiers();
    }

    protected CtClass loadClass(String className, @Nullable String... imports) throws NotFoundException {
        loaded = true;
        className = className.replace("\\", ".").replace("/", ".");
        if (className.endsWith(".class")) className = className.substring(0, className.length() - 6);
        if (pool == null) pool = ClassPool.getDefault();
        if (imports != null) {
            for (String imp : imports) {
                pool.importPackage(imp);
            }
        }
        return cc = pool.get(className);
    }

    protected CtClass setPathToClassInsideJAR(String pathToClassInsideJAR, @Nullable String... imports) throws IOException {
        loaded = false;
        this.pathToClassInsideJAR = pathToClassInsideJAR;
        if (!pathToClassInsideJAR.endsWith(".class")) this.pathToClassInsideJAR = pathToClassInsideJAR.replace(".", "/") + ".class";
        JarFile jarFile = new JarFile(pathToJAR);
        ZipEntry zipEntry = jarFile.getEntry(pathToClassInsideJAR);
        if (zipEntry == null) {
            jarFile.close();
            throw new RuntimeException("Class not found in jar file");
        }
        InputStream fis = jarFile.getInputStream(zipEntry);

        if (pool == null) pool = ClassPool.getDefault();
        if (imports != null) {
            for (String imp : imports) {
                pool.importPackage(imp);
            }
        }
        cc = pool.makeClass(fis);
        fis.close();
        jarFile.close();
        return cc;
    }

    protected void modifyClass(@Nullable String newName, @Nullable CtClass superClass, @Nullable CtClass[] interfaces, @Nullable String modifiers, @Nullable Attributes attribute) throws CannotCompileException {
        if (newName != null) cc.setName(newName);
        if (superClass != null) cc.setSuperclass(superClass);
        if (interfaces != null) cc.setInterfaces(interfaces);
        if (modifiers != null) cc.setModifiers(Integer.parseInt(modifiers));
        if (attribute != null) cc.setAttribute(attribute.name, attribute.data);
    }

    protected void modifyMethod(String methodName, String descriptor, @Nullable String body, @Nullable String newName, @Nullable String mods) throws CannotCompileException, NotFoundException {
        CtMethod cm = cc.getMethod(methodName, descriptor);
        if (body != null) cm.setBody(body);
        if (newName != null) cm.setName(newName);
        if (mods != null) cm.setModifiers(Integer.parseInt(mods));
    }

    protected void makeMethod(String method) throws CannotCompileException {
        CtMethod cm = CtNewMethod.make(method, cc);
        cc.addMethod(cm);
    }

    protected void modifyDeclaredMethod(String methodName, @Nullable String body, @Nullable String newName, @Nullable String mods) throws CannotCompileException, NotFoundException {
        CtMethod cm = cc.getDeclaredMethod(methodName);
        if (body != null) cm.setBody(body);
        if (newName != null) cm.setName(newName);
        if (mods != null) cm.setModifiers(Integer.parseInt(mods));
    }

    protected void modifyField(String fieldName, @Nullable String fieldSrc, @Nullable CtClass type, @Nullable String newName, @Nullable Attributes attribute, @Nullable String mods) throws NotFoundException, CannotCompileException {
        CtField cf = cc.getField(fieldName);
        if (fieldSrc != null) {
            cc.removeField(cf);
            cf = CtField.make(fieldSrc, cc);
            cc.addField(cf);
        }
        if (newName != null) cf.setName(newName);
        if (mods != null) cf.setModifiers(Integer.parseInt(mods));
        if (type != null) cf.setType(type);
        if (attribute != null) cf.setAttribute(attribute.name, attribute.data);
    }

    protected void makeField(String field) throws CannotCompileException {
        CtField cf = CtField.make(field, cc);
        cc.addField(cf);
    }

    protected void modifyDeclaredField(String fieldName, @Nullable String fieldSrc, @Nullable CtClass type, @Nullable String newName, @Nullable Attributes attribute, @Nullable String mods) throws NotFoundException, CannotCompileException {
        CtField cf = cc.getDeclaredField(fieldName);
        if (fieldSrc != null) {
            cc.removeField(cf);
            cf = CtField.make(fieldSrc, cc);
            cc.addField(cf);
        }
        if (newName != null) cf.setName(newName);
        if (mods != null) cf.setModifiers(Integer.parseInt(mods));
        if (type != null) cf.setType(type);
        if (attribute != null) cf.setAttribute(attribute.name, attribute.data);
    }

    protected Class<?> save() throws CannotCompileException {
        return cc.toClass();
    }

    protected void write() throws IOException, CannotCompileException {
        if (!loaded) {
            String classFileName = pathToClassInsideJAR.replace("\\", "/").replaceAll("[.](?=.*[.])", "/").substring(pathToClassInsideJAR.lastIndexOf('/') + 1);
            if (!classFileName.endsWith(".class")) classFileName += ".class";
            DataOutputStream dos = new DataOutputStream(new FileOutputStream(classFileName));
            cc.getClassFile().write(dos);
            dos.close();

            Map<String, String> envs = new HashMap<>();
            URI pathURI = URI.create("jar:" + new File(pathToJAR).toURI());
            envs.put("create", "true");
            try (FileSystem zipfs = FileSystems.newFileSystem(pathURI, envs)) {
                Files.move(Paths.get(classFileName), zipfs.getPath(pathToClassInsideJAR),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            cc.toClass();
        }
    }

    protected void end() throws IOException {
        if (!loaded) {
            URI pathURI = URI.create("jar:" + new File(pathToJAR).toURI());
            Map<String, String> envs = new HashMap<>();
            envs.put("create", "true");
            try (FileSystem zipfs = FileSystems.newFileSystem(pathURI, envs)) {
                Files.createFile(zipfs.getPath("patched"));
            }
        }
    }

    public static class Attributes {
        String name;
        byte[] data;

        protected Attributes(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }
}

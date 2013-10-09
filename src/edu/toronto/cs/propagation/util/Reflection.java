package edu.toronto.cs.propagation.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Reflection {

	/**
	 * Instantiate an object of a given class name, throw an exception if
	 * anything goes wrong.
	 * 
	 * @param name
	 *            The class name, fully specified.
	 * @return An instance of the given class
	 */
	public static Object instantiate(String name) {
		if (name == null) {
			throw new IllegalArgumentException("Name parameter was null");
		}
		Object o = null;
		try {
			o = Class.forName(name).newInstance();
		} catch (InstantiationException e) {
			throw new IllegalArgumentException("Can not instantiate: '" + name
					+ "' : " + e);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("Illegal access: '" + name
					+ "' : " + e);
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException("Can not find: '" + name + "'");
		}
		return o;
	}

	/**
	 * Returns a list of subclasses of a given class that should be.
	 * 
	 * The classes returned should: (a) in the same package as the original
	 * class and (b) contained in the same directory or jar file.
	 * 
	 * @param clazz
	 *            The superclass
	 * @return A list of subclasses.
	 * @throws FileNotFoundException
	 */
	@SuppressWarnings("rawtypes")
	public static String[] subClasses(Class clazz) {
		Vector<String> fileNames = new Vector<String>();

		URL url = getDirectoryForClass(clazz);
		if (url != null) {

			// First attempt: get list from the file system
			File dir = new File(url.getFile());
			if (dir.exists()) {
				String[] files = dir.list();
				for (String fileName : files) {
					if (fileName.endsWith(".class")) {
						fileNames.add(fileName);
					}
				}
			}

		} else {

			// Fall back: get list from the jar file
			final JarFile jarFile = getJarFileForClass(clazz);
			final Enumeration<JarEntry> entries = jarFile.entries();
			String packagePath = clazz.getPackage().getName();
			packagePath = packagePath.replace('.', '/');

			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				String fullFileName = entry.toString();
				if (fullFileName.contains(packagePath)
						&& fullFileName.endsWith(".class")) {
					String[] pathFileName = fullFileName.split("/");
					fileNames.add(pathFileName[pathFileName.length - 1]);
				}
			}
		}

		// At this point we have a list of file names (e.g."classname.class")
		// that were found in the same directory as the requested class. Now we
		// will instantiate them and check if they are sub-classes of the
		// requested class.
		Vector<String> ret = new Vector<String>();
		for (String fileName : fileNames) {
			String className = fileName.substring(0, fileName.length() - 6); // ".class"=6chars
			Object o = tryInstantiate(clazz.getPackage().getName() + "."
					+ className);
			if (o != null && clazz.isInstance(o)) {
				ret.add(className);
			}
		}
		return ret.toArray(new String[] {});
	}

	/**
	 * Try to instantiate an object, return null if can't do it. No exceptions
	 * are thrown.
	 * 
	 * @param name
	 * @return The object, or a null if the object could not be instantiated.
	 */
	public static Object tryInstantiate(String name) {
		try {
			return instantiate(name);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Obtains the directory containing the bytecode for a class
	 * 
	 * @param clazz
	 *            The class
	 * @return A URL pointing to the directory.
	 */
	private static URL getDirectoryForClass(
			@SuppressWarnings("rawtypes") Class clazz) {
		String packageName = clazz.getPackage().getName();
		String path = packageName;
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		path = path.replace('.', '/');
		URL url = clazz.getResource(path);
		return url;
	}

	/**
	 * Obtains the JAR file containing the bytecode for a class
	 * 
	 * @param clazz
	 *            The class
	 * @return A JAR file descriptor
	 */
	@SuppressWarnings("rawtypes")
	private static JarFile getJarFileForClass(Class clazz) {
		String resourceName = clazz.getName();
		resourceName = resourceName.replace('.', '/') + ".class";
		URL url = clazz.getClassLoader().getResource(resourceName);
		JarURLConnection conn;
		try {
			conn = (JarURLConnection) url.openConnection();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		try {
			return conn.getJarFile();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@SuppressWarnings("rawtypes")
	public static Object instantiate(Class clazz, String name) {
		return instantiate(clazz.getPackage().getName() + "." + name);
	}

}

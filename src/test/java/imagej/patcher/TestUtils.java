/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2014 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package imagej.patcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class TestUtils {

	/**
	 * Makes a temporary directory for use with unit tests.
	 * <p>
	 * When the unit test runs in a Maven context, the temporary directory will be
	 * created in the <i>target/</i> directory corresponding to the calling class
	 * instead of <i>/tmp/</i>.
	 * </p>
	 * 
	 * @param prefix the prefix for the directory's name
	 * @return the reference to the newly-created temporary directory
	 * @throws IOException
	 */
	public static File createTemporaryDirectory(final String prefix)
		throws IOException
	{
		return createTemporaryDirectory(prefix, getCallingClass(null));
	}

	/**
	 * Makes a temporary directory for use with unit tests.
	 * <p>
	 * When the unit test runs in a Maven context, the temporary directory will be
	 * created in the corresponding <i>target/</i> directory instead of
	 * <i>/tmp/</i>.
	 * </p>
	 * 
	 * @param prefix the prefix for the directory's name
	 * @param forClass the class for context (to determine whether there's a
	 *          <i>target/<i> directory)
	 * @return the reference to the newly-created temporary directory
	 * @throws IOException
	 */
	public static File createTemporaryDirectory(final String prefix,
		final Class<?> forClass) throws IOException
	{
		final URL directory = Utils.getLocation(forClass);
		if (directory != null && "file".equals(directory.getProtocol())) {
			final String path = directory.getPath();
			if (path != null && path.endsWith("/target/test-classes/")) {
				final File baseDirectory =
					new File(path.substring(0, path.length() - 13));
				final File file = File.createTempFile(prefix, "", baseDirectory);
				if (file.delete() && file.mkdir()) return file;
			}
		}
		return createTemporaryDirectory(prefix, "", null);
	}

	/**
	 * Returns the class of the caller (excluding the specified class).
	 * <p>
	 * Sometimes it is convenient to determine the caller's context, e.g. to
	 * determine whether running in a maven-surefire-plugin context (in which case
	 * the location of the caller's class would end in
	 * <i>target/test-classes/</i>).
	 * </p>
	 * 
	 * @param excluding the class to exclude (or null)
	 * @return the class of the caller
	 */
	public static Class<?> getCallingClass(final Class<?> excluding) {
		final String thisClassName = TestUtils.class.getName();
		final String thisClassName2 =
			excluding == null ? null : excluding.getName();
		final Thread currentThread = Thread.currentThread();
		for (final StackTraceElement element : currentThread.getStackTrace()) {
			final String thatClassName = element.getClassName();
			if (thatClassName == null || thatClassName.equals(thisClassName) ||
				thatClassName.equals(thisClassName2) ||
				thatClassName.startsWith("java.lang."))
			{
				continue;
			}
			final ClassLoader loader = currentThread.getContextClassLoader();
			try {
				return loader.loadClass(element.getClassName());
			}
			catch (ClassNotFoundException e) {
				throw new UnsupportedOperationException("Could not load " +
					element.getClassName() + " with the current context class loader (" +
					loader + ")!");
			}
		}
		throw new UnsupportedOperationException("No calling class outside " +
			thisClassName + " found!");
	}

	/**
	 * Creates a temporary directory.
	 * <p>
	 * Since there is no atomic operation to do that, we create a temporary file,
	 * delete it and create a directory in its place. To avoid race conditions, we
	 * use the optimistic approach: if the directory cannot be created, we try to
	 * obtain a new temporary file rather than erroring out.
	 * </p>
	 * <p>
	 * It is the caller's responsibility to make sure that the directory is
	 * deleted.
	 * </p>
	 * 
	 * @param prefix The prefix string to be used in generating the file's name;
	 *          see {@link File#createTempFile(String, String, File)}
	 * @param suffix The suffix string to be used in generating the file's name;
	 *          see {@link File#createTempFile(String, String, File)}
	 * @param directory The directory in which the file is to be created, or null
	 *          if the default temporary-file directory is to be used
	 * @return: An abstract pathname denoting a newly-created empty directory
	 * @throws IOException
	 */
	public static File createTemporaryDirectory(final String prefix,
		final String suffix, final File directory) throws IOException
	{
		for (int counter = 0; counter < 10; counter++) {
			final File file = File.createTempFile(prefix, suffix, directory);

			if (!file.delete()) {
				throw new IOException("Could not delete file " + file);
			}

			// in case of a race condition, just try again
			if (file.mkdir()) return file;
		}
		throw new IOException(
			"Could not create temporary directory (too many race conditions?)");
	}

	/**
	 * Deletes a directory recursively.
	 * 
	 * @param directory The directory to delete.
	 * @return whether it succeeded (see also {@link File#delete()})
	 */
	public static boolean deleteRecursively(final File directory) {
		if (directory == null) return true;
		final File[] list = directory.listFiles();
		if (list == null) return true;
		for (final File file : list) {
			if (file.isFile()) {
				if (!file.delete()) return false;
			}
			else if (file.isDirectory()) {
				if (!deleteRecursively(file)) return false;
			}
		}
		return directory.delete();
	}

	/**
	 * Bundles the given classes in a new .jar file.
	 * 
	 * @param jarFile the output file
	 * @param classNames the classes to include
	 * @throws IOException
	 */
	public static void makeJar(final File jarFile, final String... classNames)
		throws IOException
	{
		final JarOutputStream jar =
			new JarOutputStream(new FileOutputStream(jarFile));
		final byte[] buffer = new byte[16384];
		final StringBuilder pluginsConfig = new StringBuilder();
		for (final String className : classNames) {
			final String path = className.replace('.', '/') + ".class";
			final InputStream in = TestUtils.class.getResourceAsStream("/" + path);
			final ZipEntry entry = new ZipEntry(path);
			jar.putNextEntry(entry);
			for (;;) {
				int count = in.read(buffer);
				if (count < 0) break;
				jar.write(buffer, 0, count);
			}
			if (className.indexOf('_') >= 0) {
				final String name =
					className.substring(className.lastIndexOf('.') + 1).replace('_', ' ');
				pluginsConfig.append("Plugins, \"").append(name).append("\", ").append(
					className).append("\n");
			}
			in.close();
		}
		if (pluginsConfig.length() > 0) {
			final ZipEntry entry = new ZipEntry("plugins.config");
			jar.putNextEntry(entry);
			jar.write(pluginsConfig.toString().getBytes());
		}
		jar.close();
	}

	/**
	 * Instantiates a class loaded in the given class loader.
	 * 
	 * @param loader the class loader with which to load the class
	 * @param className the name of the class to be instantiated
	 * @param parameters the parameters to pass to the constructor
	 * @return the new instance
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 */
	@SuppressWarnings("unchecked")
	public static <T> T construct(final ClassLoader loader,
		final String className, final Object... parameters)
		throws SecurityException, NoSuchMethodException, IllegalArgumentException,
		IllegalAccessException, InvocationTargetException, ClassNotFoundException,
		InstantiationException
	{
		final Class<?> clazz = loader.loadClass(className);
		for (final Constructor<?> constructor : clazz.getConstructors()) {
			if (doParametersMatch(constructor.getParameterTypes(), parameters)) {
				return (T) constructor.newInstance(parameters);
			}
		}
		throw new NoSuchMethodException("No matching method found");
	}

	/**
	 * Invokes a static method of a given class.
	 * <p>
	 * This method tries to find a static method matching the given name and the
	 * parameter list. Just like {@link #newInstance(String, Object...)}, this
	 * works via reflection to avoid a compile-time dependency on ImageJ2.
	 * </p>
	 * 
	 * @param loader the class loader with which to load the class
	 * @param className the name of the class whose static method is to be called
	 * @param methodName the name of the static method to be called
	 * @param parameters the parameters to pass to the static method
	 * @return the return value of the static method, if any
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 * @throws ClassNotFoundException
	 */
	@SuppressWarnings("unchecked")
	public static <T> T
		invokeStatic(final ClassLoader loader, final String className,
			final String methodName, final Object... parameters)
			throws SecurityException, NoSuchMethodException,
			IllegalArgumentException, IllegalAccessException,
			InvocationTargetException, ClassNotFoundException
	{
		final Class<?> clazz = loader.loadClass(className);
		for (final Method method : clazz.getMethods()) {
			if (method.getName().equals(methodName) &&
				doParametersMatch(method.getParameterTypes(), parameters))
			{
				return (T) method.invoke(null, parameters);
			}
		}
		throw new NoSuchMethodException("No matching method found");
	}

	/**
	 * Check whether a list of parameters matches a list of parameter types. This
	 * is used to find matching constructors and (possibly static) methods.
	 * 
	 * @param types the parameter types
	 * @param parameters the parameters
	 * @return whether the parameters match the types
	 */
	private static boolean
		doParametersMatch(Class<?>[] types, Object[] parameters)
	{
		if (types.length != parameters.length) return false;
		for (int i = 0; i < types.length; i++)
			if (parameters[i] != null) {
				Class<?> clazz = parameters[i].getClass();
				if (types[i].isPrimitive()) {
					if (types[i] != Long.TYPE && types[i] != Integer.TYPE &&
						types[i] != Boolean.TYPE) throw new RuntimeException(
						"unsupported primitive type " + clazz);
					if (types[i] == Long.TYPE && clazz != Long.class) return false;
					else if (types[i] == Integer.TYPE && clazz != Integer.class) return false;
					else if (types[i] == Boolean.TYPE && clazz != Boolean.class) return false;
				}
				else if (!types[i].isAssignableFrom(clazz)) return false;
			}
		return true;
	}

	/**
	 * Instantiates a new {@link LegacyEnvironment} for use in unit tests.
	 * <p>
	 * In general, unit tests should not rely on, or be affected by, side
	 * effects such as the presence of plugins in a subdirectory called
	 * <code>.plugins/</code> of the user's home directory. This method
	 * instantiates a legacy environment switching off all such side effects,
	 * insofar supported by the {@link LegacyEnvironment}.
	 * </p>
	 * 
	 * @return the legacy environment
	 * @throws ClassNotFoundException
	 */
	public static LegacyEnvironment getTestEnvironment() throws ClassNotFoundException {
		final LegacyEnvironment result = new LegacyEnvironment(null, true);
		return result;
	}

}

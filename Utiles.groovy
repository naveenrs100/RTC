package es.eci.utils

import groovy.io.FileVisitResult

/**
 * Esta clase agrupa diversos métodos de utilidad para la API de groovy del 
 * servicio de IC, en los apartados de:<br/>
 * + Manipulación de ficheros<br/>
 * + Gestión de parámetros en los scripts groovy</br>
 */
class Utiles {
	
	//---------------------------------------------------------------------------
	// Métodos de la clase
	
	/**
	 * Devuelve la ruta relativa entre un fichero y un directorio.
	 * @param rootDir Directorio base.
	 * @param file Fichero/directorio cuya ruta relativa necesitamos encontrar.
	 */
	public static String rutaRelativa(File rootDir, File file) {
		String ret = "";
		if (rootDir.isDirectory() && rootDir.exists() && file.exists()) {
			String rootAbsoluta = rootDir.getCanonicalPath() 
			String fileAbsoluta = file.getCanonicalPath()
			if (fileAbsoluta.contains(rootAbsoluta)) {
				if (file.isDirectory()) {
					ret = fileAbsoluta.substring(rootAbsoluta.length())
				}
				else {
					ret = file.getParentFile().getCanonicalPath().substring(rootAbsoluta.length())
				}
			}
		}
		// Quitar el separador al principio
		if (ret != null  && ret.length() > 0) {
			if (ret.charAt(0) == System.getProperty("file.separator")) {
				ret = ret.substring(1)
			}
		}
		return ret
	}
	
	/**
	 * Búsqueda de ficheros con límite de profundidad.
	 * @param fromDirName Directorio de inicio de la búsqueda.
	 * @param fileMatch Patrón de búsqueda.
	 * @param maxDepth Límite de profundidad.
	 * Si es -1: recursividad, sin límite.
	 * Si es 0: no hace recursividad.
	 * Si es mayor que cero: límite de la recursividad.
	 */
	def static getAllFiles(fromDirName,fileMatch, max){
		def fromDir = new File(fromDirName)
		def files = []
		fromDir.traverse(
			type: groovy.io.FileType.FILES,
			preDir: { if (it.name.startsWith(".") || it.name == 'target') return FileVisitResult.SKIP_SUBTREE},
			nameFilter: ~/${fileMatch}/,
			maxDepth: max
		){
			files << it
		}
		return files
	}

	/**
	 * Búsqueda de ficheros sin límite de profundidad.
	 * @param fromDirName Directorio de inicio de la búsqueda.
	 * @param fileMatch Patrón de búsqueda.
	 */
	def static getAllFiles(fromDirName,fileMatch){
		return getAllFiles(fromDirName, fileMatch, -1);
	}
	
	/**
	 * Este método determina la profundidad de un directorio.
	 */
	def static getDepth(file) {
		File f = null
		if (file instanceof java.io.File) {
			f = file
		}
		else if (file instanceof String) {
			f = new File(file)
		}
		int ret = -1;
		if (f.isDirectory()) {
			// Si es un directorio, devolver su profundidad
			return f.getAbsolutePath().split("\\" + File.separator).size()
		}
		else {
			// Si es un fichero, la profundidad del directorio que lo contiene
			return f.getParent().split("\\" + File.separator).size()
		}
	}
	
	/**
	 * Trata de encontrar el ejemplar de menor profundidad posible de un fichero
	 * que cumpla un determinado patrón a partir de un directorio.
	 * @param fromDirName Directorio de origen (raíz).
	 * @param fileMatch Patrón a buscar.
	 * @return Instancia si existe del fichero que cumpla el patrón, que se encuentre
	 * a la mínima distancia posible de la raíz.
	 */
	def static getRootFile(fromDirName,fileMatch){
		def fromDir = new File(fromDirName)
		def file = null
		// traverse recorre los directorios en orden aleatorio en la versión
		//	de groovy en la que se probó este código.  Se intenta asegurar
		//	mediante el código a continuación que se elige el fichero de menor
		//	profundidad
		fromDir.traverse(
			type: groovy.io.FileType.FILES,
			preDir: { if (it.name.startsWith(".") || it.name == 'target') return FileVisitResult.SKIP_SUBTREE},
			nameFilter: ~/${fileMatch}/,
			maxDepth: 2
		){
			def itDepth = getDepth(it)
			if (file == null || itDepth < getDepth(file)) {
				file = it
			}
		}
		println "Encuentra: ${file}....."
		return file

	}

	
	/**
	 * Calcula el formato cerrado de una versión (le quita el -SNAPSHOT).
	 * @param version Versión a cerrar.
	 * @return Versión cerrada .
	 */
	public static String versionCerrada(String version) {
		if (version.endsWith("-SNAPSHOT")) {
			return version.substring(0, version.indexOf("-SNAPSHOT"))
		}
		else {
			return version;
		}
	}
	
	/**
	 * Crea un fichero version.txt en el workspace indicado.
	 * @param version Versión del entregable.
	 * @param groupId Group id del entregable.
	 * @param workspace Directorio de construcción.
	 */
	public static void creaVersionTxt(String version, String groupId, String workspace) {
		File fVersion = new File(workspace + "/version.txt");
		if (!fVersion.exists()) {
			fVersion.createNewFile();
		}
		fVersion.text = ("version=\"" + version + "\"\ngroupId=\"" + groupId + "\"") 
	}
	
	/** 
	 * Copia binaria de ficheros.
	 * @param fichero Fichero de origen.
	 * @param dirDestino Ruta del directorio de destino.
	 */
	public static void copy (File file, String target) {
		new File(target).mkdirs()
		def rutaDestino = target + System.getProperty("file.separator") + file.getName()
		File destino = new File(rutaDestino)
		destino.getParentFile().mkdirs()
		destino.createNewFile()
		destino.bytes = file.bytes
	}
	
	/**
	 * Copia un directorio a otro.
	 * @param origen Directorio origen.
	 * @param dest Directorio destino.
	 */
	public static void copyDirectories(File source, File target) {
		if (source != null && target != null && source.isDirectory() && source.exists()) {
			if (!target.exists()) {
				target.mkdirs()
			}
			File[] files  = source.listFiles()
			files.each { File file ->
				File fileDest = new File(target, file.getName())
				if (file.isFile()) {
					fileDest.createNewFile()
					fileDest.bytes = file.bytes
				}
				else if (file.isDirectory()) {
					copyDirectories(file, fileDest)
				}
			}
		}
	}
	
	/**
	 * Lee como boolean una cadena pasada como parámetro.
	 * @param str Cadena a leer.
	 * @return Traducción de la cadena a true/false.
	 */
	public static boolean toBoolean(String str) {
		boolean ret = false;
		if (str != null && str.trim().length() > 0) {
			ret = Boolean.valueOf(str);
		}
		return ret;
	}
	
	/**
	 * Lee como fichero una cadena pasada como parámetro.
	 * @param str Cadena a leer.
	 * @return Traducción de la cadena a fichero.
	 */
	public static File toFile(String str) {
		File ret = null;
		if (str != null && str.trim().length() > 0) {
			ret = new File(str);
		}
		return ret;
	}
	
	/**
	 * Intenta leer un parámetro opcional de los argumentos.
	 * @param args Lista de argumentos.
	 * @param index Índice del parámetro opcional.
	 * @return Nulo si el parámetro no existe, el valor del parámetro en otro
	 * caso.
	 */
	public static String readOptionalParameter(String[] args, int index) {
		String ret = null;
		
		if (index < (args.length)) {
			ret = args[index];
		}
		
		return ret;
	}
	
	// Utilidad para decidir si existe o bien si está vacía una posición de un array
	public static boolean empty(String[] array, int index) {
		return index >= array.length || array[index] == null || array[index].trim().length() == 0;
	}
	
	/**
	 * Este método valida que todas las cadenas de una lista de cadenas
	 * vengan informadas.
	 * @param args Lista de argumentos.
	 * @param limit Opcional.  Si viene informado, valida solo los limite primeros.  
	 * 	Ha de ser mayor que cero.
	 */
	public static void validate(String[] args, Integer limit = null) {
		if (limit != null && limit < 1) {
			throw new IllegalArgumentException("limite")
		}
		if (limit != null) {
			if (args.length < limit) {
				throw new Exception("¡No hay suficientes argumentos!")
			}
		}
		else {
			limit = args.length;
		}
		int counter = 0;
		for (int i = 0; i < limit; i++) {
			if (!empty(args, i)) {
				counter++;
			}
		}
		
		if (counter < limit) {
			throw new Exception("¡Falta un argumento obligatorio!")
		}
	}
}
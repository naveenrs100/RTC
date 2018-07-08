package es.eci.utils
class TarHelper {
	
	// Compone un comando shell según el sistema operativo
	private static List<String> componerComando(List<String> comandos) {
		def ret = []
		if (System.properties['os.name'].toLowerCase().contains('windows')) {
			ret = [ "cmd.exe", "/C" ]
		}
		else {
			ret = [ "sh", "-c" ]
		}
		ret.addAll(comandos)
		return ret
	}
	
	/**
	 * Descomprime un tar al directorio indicado
	 * @param tarFile Fichero con el tar
	 * @param folder Directorio destino
	 */
	public static void untarFile(File tarFile, File folder) {
		if (folder != null && folder.exists()) {
			if (tarFile != null && tarFile.exists()) {
				def comando = componerComando(["tar xvf ${tarFile.canonicalPath}"])
				def descomprimir = comando.execute(null, folder);
				StreamGobbler cout = new StreamGobbler(descomprimir.getInputStream(), true)
				StreamGobbler cerr = new StreamGobbler(descomprimir.getErrorStream(), true)
				cout.start()
				cerr.start()
		    
		    	descomprimir.waitFor()
			}
		}
	}
	
	/**
	 * Comprime un tar con el directorio indicado incluido
	 * @param folder Directorio origen
	 * @param dest Fichero de destino
	 */
	public static void tarFolder(File folder, File dest) {
		if (folder != null && folder.exists()) {
			def comando = componerComando(["tar cvf ${dest.canonicalPath} ${folder.name}"])
			def comprimir = comando.execute(null, folder.getParentFile());
			StreamGobbler cout = new StreamGobbler(comprimir.getInputStream(), true)
			StreamGobbler cerr = new StreamGobbler(comprimir.getErrorStream(), true)
			cout.start()
			cerr.start()
	    
	    	comprimir.waitFor()		
		}
	}
	
	/**
	 * Comprime un tar con el contenido del directorio indicado
	 * @param folder Directorio origen
	 * @param dest Fichero de destino
	 */
	public static void tarFile(File folder, File dest) {
		if (folder != null && folder.exists()) {
			def comando = componerComando(["tar cvf ${dest.canonicalPath} *"])
			def comprimir = comando.execute(null, folder);
			StreamGobbler cout = new StreamGobbler(comprimir.getInputStream(), true)
			StreamGobbler cerr = new StreamGobbler(comprimir.getErrorStream(), true)
			cout.start()
			cerr.start()
	    
	    	comprimir.waitFor()		
		}
	}
}
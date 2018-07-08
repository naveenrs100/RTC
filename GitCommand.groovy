package es.eci.utils

import java.io.File;

import es.eci.utils.base.Loggable;
import es.eci.utils.commandline.CommandLineHelper;

class GitCommand extends Loggable {

	/**
	 * Ejecuta un comando git del tipo "git comando user@host:path
	 * @param comando
	 * @param usr
	 * @param pwd
	 * @param urlRepo
	 * @param baseDir
	 * @return
	 */
	public String ejecutarComando(String comando, String usr, String pwd, String urlRepo, File baseDir) {
		String cadena = "\"${this.gitHome}/${this.comando.toString()}\" ${this.configFragment(baseDir)} " + comando + (urlRepo!=null?" -r ${urlRepo} ":"") + " -u ${usr} -P ${pwd}";
		CommandLineHelper helper = buildCommandHelper(cadena)
		helper.initLogger(this)
		lastResult = helper.execute(baseDir)
		errorOutput = helper.getErrorOutput();
		if (Commands.SCM == this.comando) {
			log "Limpiando el directorio temporal de metadatos ${tempDir.getCanonicalPath()}..."
			tempDir.deleteDir()
		}
		// Leer la salida del fichero
		return helper.getStandardOutput()
	}

}

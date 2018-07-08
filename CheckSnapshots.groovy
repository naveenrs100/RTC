package es.eci.utils

import urbanCode.UrbanCodeExecutor
import es.eci.utils.base.Loggable
import groovy.json.*

public class CheckSnapshots extends Loggable {

	//-------------------------------------------------------------
	// Métodos de la clase

	/** Comprobación de existencia de stream en RTC
	 * @param stream Nombre del Stream a comprobar
	 * @param rtcUser usuario de RTC
	 * @param rtcPass password de RTC
	 * @param rtcUrl url de RTC
	 */
	public boolean checkRTCstreams(stream, rtcUser, rtcPass, rtcUrl) {
		def scm = new ScmCommand(ScmCommand.Commands.SCM);
		scm.initLogger(this);
		def commandByStream = "list streams -n \"${stream}\"";

		def rtcStreams = null;
		TmpDir.tmp { File baseDir ->
			rtcStreams = scm.ejecutarComando(commandByStream, rtcUser, rtcPass, rtcUrl, baseDir);
		}
		return (!rtcStreams.trim().equals("") ? true : false);
	}

	/** Comprobación de snapshots en RTC
	 * @param stream Junto a instantantea formará el nombre de la snapshot RTC
	 * @param instantanea Junto a stream formará el nombre de la snapshot RTC 
	 * @param rtcUser usuario de RTC
	 * @param rtcPass password de RTC
	 * @param rtcUrl url de RTC
	 */
	public boolean checkRTCSnapshots(stream, instantanea, rtcUser, rtcPass, rtcUrl){
		def scm = new ScmCommand(ScmCommand.Commands.SCM);
		scm.initLogger(this);
		def rtc_snapshot = stream + " - " + instantanea;
		def commandByInstantanea =
				"get attributes --snapshot \"${instantanea}\" --snapshot-workspace \"${stream}\" --ownedby -j"
		def commandBySnapshot =
				"get attributes --snapshot \"${rtc_snapshot}\" --snapshot-workspace \"${stream}\" --ownedby -j"

		int snapshotsByInstantaneaJson = 1;
		int snapshotsByStreamJson = 1;

		TmpDir.tmp { File baseDir ->
			scm.ejecutarComando(commandByInstantanea, rtcUser, rtcPass, rtcUrl, baseDir);
			snapshotsByInstantaneaJson = scm.getLastResult();

			scm.ejecutarComando(commandBySnapshot, rtcUser, rtcPass, rtcUrl, baseDir);
			snapshotsByStreamJson = scm.getLastResult();
		}

		log("snapshotsByInstantaneaJson -> " + snapshotsByInstantaneaJson)
		log("snapshotsByStreamJson -> " + snapshotsByStreamJson)

		def resultado = ((snapshotsByInstantaneaJson == 0) || (snapshotsByStreamJson == 0));

		return resultado;
	}

	/** Comprobación de snapshots en UrbanCode
	 * @param application Aplicación en la que se consulta la existencia de
	 * 	la instantánea
	 * @param instantanea Nombre de la instantánea a comprobar
	 * @param urbExe Objeto para interactuar con Urban Code
	 * @return Cierto si la instantánea consultada existe en Urban Code
	 */
	public boolean checkUrbanCodeSnapshots(
			UrbanCodeExecutor urbExe,
			String application,
			String instantanea) {
		urbExe.initLogger(this);
		def urbanSnapshot = null;
		def result = false;
		try {
			urbanSnapshot = urbExe.
					executeCommand(
					"getSnapshot --application \"${application}\" --snapshot \"${instantanea}\"");
		} catch (Exception e) {
			if(e.message.contains("The value given for \"application\"")) {
				log("No está dada de alta la aplicacion \"${application}\" en UrbanCode")
			} else if(e.message.contains("The value given for \"snapshot\"")) {
				log("No está dada de alta la snapshot \"${instantanea}\" en UrbanCode")
			}
			else {
				throw new Exception(e);
			}
		}
		// Si nos ha devuelto un valor significa que la instantánea está dada de alta.
		if(urbanSnapshot != null) {
			result = true;
		}
		return result;
	}

}

package es.eci.utils;

import es.eci.utils.Stopwatch;
import es.eci.utils.TmpDir;
import es.eci.utils.base.Loggable

/** 
 * Este script esta pensado para borrrar o modificar ciertos ficheros, directorios o variables
 * que sean necesarios para el correcto funcionamiento de un workflow o step. La inclusion de este
 * paso "stepCleanAdHoc" no debe interferir en el funcionamiento del workflow, sea utilizado o no.
 * Debe minimizarse el uso de este script si existe una alternativa con menos impacto.
 */

public class CleanAdHoc extends Loggable {
	
	//---------------------------------------------------------------
	// Propiedades de la clase
		
	// Obligatorios	
	private String casens;
	private String parentWorkspace;

	/**
	 * Cada funcion del script se llamara "caseXX" (donde XX es una numero correlativo) y su cometido se
	 * describira en la cabecera de la misma. Indicando claramente en que proyecto/corriente/componente 
	 * se usa. Asi mismo, si se modifica su ubicacion es responsabilidad de la persona que lo modifica 
	 * actualizar la documentacion de la cabecera. 
	 */
	public void execute() {
		TmpDir.tmp { File daemonsConfigDir ->			
			// Validación de obligatorios
			ParameterValidator.builder()
					.add("parentWorkspace", parentWorkspace)
					.add("casens", casens).build().validate();
			
			long millis = Stopwatch.watch {
				if ( this.casens != null ) {
					switch (this.casens) {
						case 'case01':
							case01()
					    break
					    default:
					        log ""
					    break
					}
				}
			}
			
			log "Tiempo de ejecucion: ${millis} mseg."
			
		}
	}	

	/**
	 * Descripcion: Busca el fichero .extensibilidad-xjcStaleFlag en una ruta concreta y lo elimina
	 * Ubicacion: Se utiliza en el componente CCCC-SSP_109_01S_SB-DESARROLLO -COMP- 6BR-LIBS
	 * 		Esta presente en los workflow workflowBuildMavenRTC, workflowDeployMavenRTC,
	 * 		workflowReleaseMavenRTC, workflowFixMavenRTC
	 */
	private void case01() {
		String ruta = this.parentWorkspace + "/6BR-LIB-XSD/target/jaxb2/.extensibilidad-xjcStaleFlag"
		String command = "";
		
		if (System.getProperty('os.name').toLowerCase().contains('windows')) {
			command = "del ${ruta}"
		} else {
			command = "rm -f ${ruta}"
		}
		
		def sout = new StringBuilder(), serr = new StringBuilder()
		
		// println ">" + command
		
		def proc = command.execute()
		proc.consumeProcessOutput(sout, serr)
		proc.waitForOrKill(1000)
		println "out> $sout err> $serr"
	}
	
	/**
	 * @param workspace ruta de trabajo a partir de la que se ejecutarán las acciones.
	 */
	public void setParentWorkspace(String parentWorkspace) {
		this.parentWorkspace = parentWorkspace;
	}
	
	/**
	 * @param casens Acción a realizar, depende de la parametrización interna de la clase.
	 */
	public void setCasens(String casens) {
		this.case = casens;
	}
	
}


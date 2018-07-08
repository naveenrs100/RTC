package es.eci.utils

import hudson.model.*
import groovy.json.*
import groovy.xml.*
import groovy.io.FileVisitResult

/**
 * Agrupa funcionalidad de lectura de versión, actualización, para MAVEN
 */
class MavenUtils {

	/**
	 * Intenta resolver los valores posibles de la versión
	 * de un pom a una String. En el peor caso:
	 ...
	 <version>${core-version}</version>
	 ...
		 <properties>
		 <toolkit-version>21.0.0</toolkit-version>
		 <core-version>${toolkit-version}-SNAPSHOT</core-version>
		 </properties>

	 Debería resolver la versión a:
	 21.0.0-SNAPSHOT
	 */
	def static solve(pom, version) {
		StringBuilder sb = new StringBuilder();
		List<String> tokens = parse(version);
		for(String token: tokens) {
			if (token.startsWith('${') && token.endsWith('}')) {
				// Inmersión recursiva (resolviendo antes la propiedad)
				sb.append(solve(pom, lookupProperty(pom, token.substring(2, token.length() - 1))));
			} else {
				// Caso trivial
				sb.append(token);
			}
		}
		return sb.toString();
	}
	
	/**
	 * Intenta resolver una propiedad contra el elemento <properties/>
	 * de un pom.xml
	 */
	def static lookupProperty(pom, value) {
		String ret = '';
		if (pom.properties != null && pom.properties.size() > 0) {
			pom.properties.get(0).children().each {
				if ((it.name() instanceof groovy.xml.QName) && it.name().localPart == value) {
					ret = it.text();
				}
				else if ((it.name() instanceof java.lang.String) && it.name() == value) {
					ret = it.text();
				}
			}
		}
		return ret;
	}
	
	
	/**
	 * Indica si la versión está expresada como un valor explícito (true) o
	 * bien si está indicada como referencia a una variable (false).
	 * P. ej.:
	 * <version>2.0.49.0-SNAPSHOT</version> -> devuelve true
	 * <version>${tpvVersion}</version> 	-> devuelve false
	 * @param pom Objeto resultante de parsear un pom.xml
	 */
	def static isExplicitVersion(pom) {
		boolean ret = false;
		if (pom != null) {
			if (pom.version != null && pom.version.text().trim().length() > 0) {
				ret = !pom.version.text().contains('${');
				println "Versión explícita: $ret"
			}
			else if (pom.parent.version != null && pom.parent.version.text().trim().length() > 0) {
				ret = !pom.parent.version.text().contains('${');
			}
		}
		return ret;
	}
	
	/**
	 * Este método debe asegurarse de que, en el caso de que la versión no sea
	 *	explícita (sino que esté expresada como referencia a una variable), dicha
	 *	versión esté expresada como referencia a una sola variable.  De esta forma:
	 *	<version>${miVariable}</version>	->	Válida (el método no hace nada)
	 *	<version>${miVariable}-${otraVariable}-${otraVariableMás}</version>
	 *		-> Salta una excepción
	 */
	def static validateNotExplicitVersion(pom) {
		String version = null;
		if (pom.version[0] != null) {
			version = pom.version[0].text();
		}else if (pom.parent.version[0] != null) {
			version = pom.parent.version[0].text();
		}
		if (version != null) {
			// Validar la versión
			List componentes = parse(version);
			if (componentes.size() > 1) {
				throw new Exception("La versión ($version), si no es explícita, debe estar expresada en función de una sola variable");
			}
		}
	}
	
	/**
	 * Parsea una cadena en sus componentes, separando propiedades
	 * Por ejemplo:
	 * 'aaa' -> ['aaa']
	 * 'asldkf${pom-variable}B' -> ['asldkf', '${pom-variable}', 'B']
	 * '${pom-variable-1}-${pom-variable2}-${pom-variable3}' -> ['${pom-variable-1}', '-', '${pom-variable-2}', '-', '${pom-variable-3}']
	 * '${pom-variable}-SNAPSHOT' -> ['${pom-variable}', '-SNAPSHOT']
	 */
	def static parse(String s) {
		List<String> ret = null;
		if (s != null) {
			ret = new LinkedList<String>();
			int counter = 0;
			while (counter < s.length()) {
				int forward = counter;
				if (s.charAt(counter) == '$') {
					// Variable
					while(forward < s.length() && s.charAt(forward) != '}') {
						forward++;
					}
					// Consumir el último '}'
					forward++;
				}
				else {
					while (forward < s.length() && s.charAt(forward) != '$') {
						forward++;
					}
				}
				ret.add(s.substring(counter, forward));
				counter = forward;
			}
		}
		return ret;
	}

	def static writeJsonArtifactsMaven(artifacts,home){
		def file = new File("${home}/artifacts.json")
		file.delete()
		file << "["
		def size = artifacts.size()
		artifacts.eachWithIndex { artifact, index ->
			if (index == size - 1) {
				file << "{\"version\":\"${artifact.version}\",\"groupId\":\"${artifact.groupId}\",\"artifactId\":\"${artifact.artifactId}\"}"
			}
			else {
				file << "{\"version\":\"${artifact.version}\",\"groupId\":\"${artifact.groupId}\",\"artifactId\":\"${artifact.artifactId}\"},"
			}
		}
		file << "]"
	}

	def static getArtifactsMaven(ficheros){
		def artifacts = []
		ficheros.each { fichero ->
			try {
				def pom = new XmlParser().parse(fichero)
				artifacts.add(getArtifactMaven(pom))
			}
			catch(Exception e) {
				println "Error parseando el fichero $fichero: ${e.message}"
			}
		}
		return artifacts
	}

	def static getArtifactMaven(pom){
		def artifact = new Artifact()
		artifact.version = pom.version.text()
		if (artifact.version==null || artifact.version.length()==0)
			artifact.version = pom.parent.version.text()
		artifact.artifactId = pom.artifactId.text()
		artifact.groupId = pom.groupId.text()
		if (artifact.groupId==null || artifact.groupId.length()==0)
			artifact.groupId = pom.parent.groupId.text()
		return artifact
	}


	def static processSnapshotMaven(ficheros,home){
		def result = false
		def err = new StringBuffer()
		def artifacts = getArtifactsMaven(ficheros)
		ficheros.each { fichero ->
			def pom = new XmlParser().parse(fichero)
			pom.dependencies.dependency.each { dependency ->
				def artifact = getArtifactMaven(dependency)
				if (artifact.version!=null){
					if (artifact.version.toLowerCase().indexOf("snapshot")>0){
						if (artifacts.find{it.equals(artifact)}!=null){
							result = true
						}else{
							err << "MavenUtils: There is a snapshot version in ${dependency.artifactId.text()} inside ${fichero}\n"
						}
					}
				}
			}
		}
		if (err.length()>0)
			throw new NumberFormatException("${err}")
		// escribe artifacts para ser usado por stepFileVersioner.groovy para quitar los snapshots
		writeJsonArtifactsMaven(artifacts,home)
		return result
	}
	
	/**
	 * Cambia el valor de una propiedad en un pom.xml
	 */
	def static setProperty(pom, property, value) {
		if (pom.properties != null && pom.properties.size() > 0) {
			pom.properties.get(0).children().each {
				if ((it.name() instanceof groovy.xml.QName) && it.name().localPart == property) {
					it.setValue(value);
				}
				else if (it.name() == property) {
					it.setValue(value);
				}
			}
		}
	}
	
}

public class Artifact {
	public String version
	public String groupId
	public String artifactId
	public boolean equals (object){
		if (object!=null){
			if (object.groupId==groupId && object.artifactId == this.artifactId)
				return true
		}
		return false
	}
}

package es.eci.utils

import java.text.Normalizer

import rtc.ProjectAreasMap;

class StringUtil {

	def static cleanHTML(cadena){
		if (cadena!=null){
			cadena = cadena.replaceAll("<","&lt;")
			cadena = cadena.replaceAll(">","&gt;")
			cadena = cadena.replaceAll("&","&amp;")
			cadena = cadena.replaceAll("\"","&quot;")
		}
		return cadena
	}

	def static cleanBlank(cadena){
		if (cadena!=null){
			cadena = cadena.replaceAll(" - ","_")
			cadena = cadena.replaceAll(" ","_")
			cadena = cadena.replaceAll("/","-")
		}
		return cadena
	}

	def static clean(cadena){
		if (cadena!=null){
			cadena = cadena.replaceAll("/","-")
		}
		return cadena
	}
	
	def static boolean isNull(String cadena){
		return cadena == null || cadena.trim().length() == 0;
	}
	
	def static boolean notNull(String s) {
		return !isNull(s);
	}
	
	def static removeLastComma(String text) {
		def result;
		if(text.endsWith(",")) {
			result = text.substring(0, text.length() - 1);
		} else {
			result = text;
		}
		return result;
	}
	
	// Normaliza un nombre con espacios a guiones bajos
	def static String normalize(String s) {
		return s.
				replaceAll(/\)$/, "").
				replaceAll(/^\(/, "").
				replaceAll(/\s+-\s*/, "-").
				replaceAll(/\s*-\s+/, "-").
				replaceAll(/[\s\(\)]+/, "-");
	}
	
	/**
	 * Elimina los acentos de una cadena.  P. ej.:
	 * Analítica de Ventas ->
	 * Analitica de Ventas 
	 * @param s Cadena a revisar
	 * @return Cadena con las vocales acentuadas sustituidas por la vocal
	 * correspondiente sin acentuar. 
	 */
	def static String removeAccents(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFKD).
				replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
	}
	
	// Normaliza un nombre de área de proyecto
	// Le quita cualquier prefijo que pudiera tener
	// Le quita cualquier sufijo como (RTC)
	def static String normalizeProjectArea(String s) {
		return ProjectAreasMap.beautify(s);
	}
	
	/* Elimina el sufijo del nombre de corriente
	   Los sufijos buscados son
	   ' - DESARROLLO'
	   ' - INTEGRACION'
	   ' - RELEASE'
	   ' - MANTENIMIENTO'
	   ' - PRODUCCION'
	   ' - ACTIVA'
	   '-DESARROLLO'
	   '-RELEASE'
	   '-MANTENIMIENTO'
	   '-ACTIVA'
	 */
	def static String trimStreamName(String s) {
		String tmp = s;
		def toDelete = [
		   ' - Desarrollo',
		   ' - DESARROLLO',
		   ' - INTEGRACION',
		   ' - RELEASE',
		   ' - MANTENIMIENTO',
		   ' - PRODUCCION',
		   ' - ACTIVA',
		   '-DESARROLLO',
		   '-RELEASE',
		   '-MANTENIMIENTO',
		   '-ACTIVA'
		]
		toDelete.each {
			tmp = tmp.replaceAll(it, "").trim();
			tmp = tmp.replaceAll(it.toLowerCase(), "").trim();
		}
		return tmp;
	}
}
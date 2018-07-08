package es.eci.utils

import hudson.model.*
import jenkins.model.*

/**
 * Esta clase implementa métodos de utilidad para el acceso a parámetros de cualquier
 * ejecución viva en jenkins.
 */
class ParamsHelper {

	/**
	 * Devuelve el valor de un parámetro del build.
	 * @param build Referencia al build de jenkins
	 * @param key Clave del parámetro
	 * @return Valor del parámetro si existe; null en otro caso
	 */
	public static Object getParam(build, String key) {
		def ret = null;
		def paramsIn = build?.actions.find{ it instanceof ParametersAction }?.parameters
		if (paramsIn!=null){
			ret = paramsIn.find { it.name.equals(key) }?.value
		}
		return ret;
	} 
	
	/**
	 * Añade una tabla de parejas clave/valor a los parámetros actuales del build.
	 * @param build Objeto build de jenkins
	 * @param theParams Tabla de parámetros definidos por clave / valor
	 */
	public static void addParams(build, Map<String, String> theParams) {
		def paramsIn = build?.actions.find{ it instanceof ParametersAction }?.parameters
		def index = build?.actions.findIndexOf{ it instanceof ParametersAction }
		def params = [];
		if (paramsIn!=null){
			//No se borra nada para compatibilidad hacia atrás.
			params.addAll(paramsIn);
			//Borra de la lista los paramaterAction
			build?.actions.remove(index);
		}
		theParams.keySet().each { String key ->
			String value = theParams.get(key);
			params.add(new StringParameterValue(key, value));
		}
		build?.actions.add(new ParametersAction(params));
	}

	/**
	 * Elimina una serie de parámetros de la lista de parámetros de entrada del build.
	 * @param build Objeto build de Jenkins.
	 * @param theParams Lista de parámetros a borrar.
	 */
	public static void deleteParams(build, String [] paramsToDelete) {
		def paramsIn = build?.actions.find{ it instanceof ParametersAction }?.parameters
		def index = build?.actions.findIndexOf{ it instanceof ParametersAction }
		def params = [];
		if (paramsIn!=null) {
			for(param in paramsIn) {
				// Añadimos sólo los parámetros que no están en
				// la lista de parámetros a borrar.
				if(!paramsToDelete.contains(param.getName())) {
					params.add(param)
				}
			}
			//Borra de la lista los paramaterAction
			build?.actions.remove(index);
		}
		build?.actions.add(new ParametersAction(params));
	}
	
	/**
	 * Dado un job de jenkins, busca entre la definición de sus parámetros
	 * el indicado, y si existiera trata de devolver su valor por defecto.
	 * @param job Definición de job de jenkins
	 * @param parameter Parámetro buscado
	 * @return El valor por defecto del parámetro buscado en el job de jenkins
	 * si existiese, nulo en otro caso
	 */
	public static String getDefaultParameterValue(job, String parameter) {
		String ret = null;
		if (job != null && parameter != null  && parameter.trim().length() > 0) {
			job.getProperties().values().each {
				if(it instanceof hudson.model.ParametersDefinitionProperty) {
					if (it.getParameterDefinition(parameter) != null) {
						ret = it.getParameterDefinition(parameter).
							getDefaultParameterValue().getValue();
					}
				}
			}
		}
		return ret;
	}
}


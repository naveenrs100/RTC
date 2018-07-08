package es.eci.utils

import hudson.model.*
import jenkins.model.*

class JenkinsUtils {

	def static setParams(build,params, bLog){
		def paramsIn = build?.actions.find{ it instanceof ParametersAction }?.parameters
		def index = build?.actions.findIndexOf{ it instanceof ParametersAction }
		def paramsTmp = []
		if (paramsIn!=null){
			//No se borra nada para compatibilidad hacia atrás.
			paramsTmp.addAll(paramsIn)
			//Borra de la lista los paramaterAction
			build?.actions.remove(index)
		}
		paramsTmp.addAll(params)
		if (bLog){
			println "-------PARAMETROS RESULTANTES--------"
			paramsTmp.each() { println " ${it}" };
			println "-------------------------------"
		}
		build?.actions.add(new ParametersAction(paramsTmp))
	}

}
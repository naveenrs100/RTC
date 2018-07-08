package es.eci.utils

import extract.excel.ExcelBuilder
import java.text.DecimalFormat
import java.util.*

class ConfigReader {

	// El resto tomado como texto
	def static datamaps = [ "workItem" : "number", "options" : "object", "pasosEnvio" : "html", "scriptSmokeTest" : "html"]
	def static nosuffix = [ "stream" , "component" ]

	def static getConfigFromExcel(configJobs,configExcel, hoja){
		return getConfigFromExcel(configJobs,configExcel, hoja, null)
	}

	def static getConfigFromExcel(configJobs, configExcel, hoja, sufijo){
		def configMerge = []
		def excel = new ExcelBuilder(configExcel)
		def etiquetas = excel.getLabels(hoja)
		def suf = sufijo!=null?"_${sufijo}":""
		excel.eachLine([labels:false, offset: 1, sheet: hoja]) {
			def configNew = [:]
			etiquetas.eachWithIndex { etiqueta, i ->
				if (nosuffix.find {it==etiqueta}==null)
					configNew.put((String)"${etiqueta}${suf}", getValue(etiqueta,cell(i)))
				else
					configNew.put(etiqueta, getValue(etiqueta,cell(i)))
			}
			configMerge.add(configNew)
		}
		return merge(setNulls(configJobs,etiquetas,sufijo),configMerge)
	}

	def static setNulls(configJobs, etiquetas, sufijo){
		def suf = sufijo!=null?"_${sufijo}":""
		configJobs.each { config ->
			etiquetas.each { etiqueta ->
				def label = nosuffix.find {it==etiqueta}==null?(String)"${etiqueta}${suf}":etiqueta
				if (config.get(label)==null){
					config.put(label,null)
				}
			}
		}
	}

	def static isEquals(config1, config2){
		if (config2!=null && config1!=null){
			if ((config1.stream == config2.stream && config2.component == null) ||
				(config1.stream == config2.stream && config1.component == config2.component))
				return true
		}
		return false
	}

	def static getDefaults(configExcel, tecnologias){
		def excel = new ExcelBuilder(configExcel)
		def result = [:]
		tecnologias.each{ tec ->
			def defaults = [:]
			excel.eachLine([sheet: "DEFAULT ${tec.toUpperCase()}"]) {
				defaults.put((String)cell(0),getValue(cell(0),cell(1)))
			}
			result.put(tec,defaults)
		}
		return result
	}

	def static setDefaults(config, defaults){
		defaults.each { key, defaultValue ->
			def value = config.get(key)
			if (value==null || value==""){
				config.put(key,defaultValue)
			}
		}
		return config
	}

	def static getValue(key, value){
		def type = datamaps.get(key)
		def valor = value
		if (value!=null && value!=""){
			switch (type){
				case "number":
					valor = new DecimalFormat("#").format(value.toFloat())
					break
				case "object":
					valor = new GroovyShell(new Binding()).evaluate(value)
					break
				case "html":
					valor = StringUtil.cleanHTML(value)
					break
				default:
					valor = value
					break
			}
		}
		return valor
	}

	def static merge(configJobs, configJobsMerge){
		configJobsMerge.each { config ->
			def configEquals = configJobs.findAll { configComp ->
				isEquals(configComp,config)
			}
			if (configEquals!=null){
				configEquals.each { configFinal ->
					config.each { key, value ->
						if (value==null || value==""){
							def valueTmp = configFinal.get(key)
							value = valueTmp!=null?valueTmp:value
						}
						configFinal.put(key,value)
					}
				}
				if (config.activo=="false"){
					configJobs.removeAll(configEquals)
				}
			}
		}
		return configJobs.size()==0?configJobsMerge:configJobs
	}

}
package es.eci.utils;

import es.eci.utils.base.Loggable
import git.GitlabClient
import groovy.json.JsonSlurper

class CheckGitParameters extends Loggable {

	
	/**
	 * Comprueba que un determinado grupo existe en gitlab
	 * @return
	 */
	public static boolean checkGitGroup(gitGroup, urlGitlab, privateGitLabToken, keystoreVersion, urlNexus) {
		boolean ret = true; 
		GitlabClient gitLabClient = new GitlabClient(urlGitlab, privateGitLabToken, keystoreVersion, urlNexus);		
		
		def entity = "groups/${gitGroup}";
		def jsonResponse = gitLabClient.get(entity, null);
		
		println("jsonResponse desde checkGitGroup ->");
		println(jsonResponse);
		
		if(jsonResponse.contains("404 Not found")) {
			ret = false;	
		}		
		return ret;
	}	
	
	
	
	
}

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsyncClientBuilder
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model.DeleteRolePolicyRequest
import com.amazonaws.services.identitymanagement.model.DeleteRoleRequest
import com.amazonaws.services.identitymanagement.model.ListRolePoliciesRequest
import com.amazonaws.services.identitymanagement.model.ListRolesRequest

@GrabResolver(name="jcenter", root="http://jcenter.bintray.com/", m2Compatible=true)
@GrabResolver(name="codehaus", root="http://repository.codehaus.org/", m2Compatible=true)

/**
 * The dependencies
 */
@Grapes([
        @Grab(group='com.amazonaws', module='aws-java-sdk', version='1.11.+')
])

def main() {
    AmazonIdentityManagement iam = new AmazonIdentityManagementClient();
    def marker = null
    def count = 0
    while ({
        ListRolesRequest req = new ListRolesRequest()
        if (marker != null && marker != "") {
            req.withMarker(marker)
        }

        def res = iam.listRoles(req)

        res.getRoles().each { role ->
            if (role.getRoleName().startsWith("cerberus-gatling-perf-role-")) {
                println "Preparing to delete role: ${role.getRoleName()}"

                iam.listRolePolicies(
                        new ListRolePoliciesRequest().withRoleName(role.getRoleName())
                ).getPolicyNames().each { policy ->
                    println "deleting policy: ${policy}"
                    iam.deleteRolePolicy(new DeleteRolePolicyRequest().withRoleName(role.getRoleName()).withPolicyName(policy))
                }
                iam.deleteRole(new DeleteRoleRequest().withRoleName(role.getRoleName()))
                count = count + 1
            }
        }

        marker = res.getMarker()
        marker != null && marker != ""
    }()) continue;

    println "deleted ${count} roles"
}

println "Starting"
main()
println "Finished"
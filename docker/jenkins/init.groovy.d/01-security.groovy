import jenkins.model.Jenkins
import hudson.security.HudsonPrivateSecurityRealm
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy

def j = Jenkins.get()

def realm = new HudsonPrivateSecurityRealm(false)
realm.createAccount('admin', 'admin')
j.setSecurityRealm(realm)

def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
strategy.setAllowAnonymousRead(false)
j.setAuthorizationStrategy(strategy)

j.save()
println '[init] security configured: admin/admin'

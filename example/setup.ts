// Clear Fabric globals to force legacy mode
// Use type assertion to handle strict typing
const g = global as Record<string, unknown>;

delete g.nativeFabricUIManager;
delete g.__fabricUIManagerBinding;
delete g.__getFabricUIManager;
delete g.__globalObjectProxy;

{
    mods.push(crate::game::native_hooks::NativeMod {
        id: "complex_java_port".to_string(),
        name: "Complex Java Mod Port".to_string(),
        version: "0.1.0".to_string(),
        description: crate::game::complex_java_port_runtime::summary().to_string(),
    });
}

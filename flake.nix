{
  description = "A flake creating the developer environment of drm-shell";
  
  inputs = {
    nixpkgs-jdk20.url = "github:tenaf0/nixpkgs/openjdk20";
    jextract.url = "github:tenaf0/jextract-flake";
    jextract.inputs.nixpkgs.follows = "nixpkgs";
    jextract.inputs.flake-utils.follows = "flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, nixpkgs-jdk20, jextract }: 
    flake-utils.lib.eachDefaultSystem (system: let
      pkgs = nixpkgs.legacyPackages.${system};
      openjdk20 = nixpkgs-jdk20.legacyPackages.${system}.openjdk20;
      gradle = pkgs.gradle.override { javaToolchains = [ openjdk20 ]; };
      runtimeLibPath = nixpkgs.lib.makeLibraryPath [ pkgs.libdrm pkgs.udev pkgs.libinput pkgs.libseat ];
    in {
      devShell = pkgs.mkShell {
        buildInputs = [ jextract.outputs.defaultPackage.${system} gradle ];
        shellHook = ''
          export LINUX_HEADERS=${pkgs.linuxHeaders}
          export GLIBC_HEADERS=${pkgs.glibc.dev}
          export LIBDRM_HEADERS=${pkgs.libdrm.dev}
          export UDEV_HEADERS=${pkgs.udev.dev}
          export LIBINPUT_HEADERS=${pkgs.libinput.dev}
          export LIBSEAT_HEADERS=${pkgs.libseat.dev}

          export LD_LIBRARY_PATH=${runtimeLibPath}:$LD_LIBRARY_PATH

          export JDK20=${openjdk20}/lib/openjdk
	      export JAVA_HOME=$JDK20
          export JAVA_OPTS="--enable-preview"
        '';
      };
    });
}

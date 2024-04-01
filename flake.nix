{
  description = "A flake creating the developer environment of drm-shell";
  
  inputs = {
    jextract.url = "github:tenaf0/jextract-flake";
    jextract.inputs.nixpkgs.follows = "nixpkgs";
    jextract.inputs.flake-utils.follows = "flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, jextract }: 
    flake-utils.lib.eachDefaultSystem (system: let
      pkgs = nixpkgs.legacyPackages.${system};
      gradle = pkgs.gradle;
      runtimeLibPath = nixpkgs.lib.makeLibraryPath [ pkgs.libdrm pkgs.udev pkgs.libinput pkgs.libseat pkgs.libGL ];
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

          export JDK22=${pkgs.openjdk22}/lib/openjdk
          export JAVA_HOME=$JDK22
          export JAVA_OPTS="--enable-preview"
        '';
      };
    });
}

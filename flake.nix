{
  description = "A very basic flake";
  
  inputs = {
    nixpkgs-jdk20.url = "github:tenaf0/nixpkgs/openjdk20";
    jextract.url = "github:tenaf0/jextract-flake";
  };

  outputs = { self, nixpkgs, flake-utils, nixpkgs-jdk20, jextract }: 
    flake-utils.lib.eachDefaultSystem (system: let
      pkgs = nixpkgs.legacyPackages.${system};
      openjdk20 = nixpkgs-jdk20.legacyPackages.${system}.openjdk20;
      runtimeLibPath = nixpkgs.lib.makeLibraryPath [ pkgs.libdrm pkgs.udev pkgs.libinput pkgs.libseat ];
    in {
      devShell = pkgs.mkShell {
        buildInputs = [ jextract.outputs.defaultPackage.${system} pkgs.openjdk17 ];
        shellHook = ''
          export LINUX_HEADERS=${pkgs.linuxHeaders}
          export GLIBC_HEADERS=${pkgs.glibc.dev}
          export LIBDRM_HEADERS=${pkgs.libdrm.dev}
          export UDEV_HEADERS=${pkgs.udev.dev}
          export LIBINPUT_HEADERS=${pkgs.libinput.dev}
          export LIBSEAT_HEADERS=${pkgs.libseat.dev}

          export LD_LIBRARY_PATH=${runtimeLibPath}:$LD_LIBRARY_PATH

          export JDK20=${openjdk20}/lib/openjdk
        '';
      };
    });
}

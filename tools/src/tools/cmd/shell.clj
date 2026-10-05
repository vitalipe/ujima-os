(ns tools.cmd.shell
  "The `shell` noun — the dev loop for desktop/shell: `sim` serves a fake desktop tier from this
   machine and points a Pi's shell at it. The work is tools.shell.sim."
  (:require [tools.shell.sim :as sim]))


(def ^:private ssh-spec
  {:ip       {:desc "The dev Pi whose shell to point here" :require true :coerce :string}
   :user     {:desc "SSH user"     :default "ujima" :coerce :string}
   :password {:desc "SSH password" :default "ujima" :coerce :string}
   :port     {:desc "SSH port"     :default "22"    :coerce :string}})


(def cli
  {"shell"
   {"sim"
    {"up"
     {:usage "Usage: shell sim up <ip> [--state <name>] [--http-port 1346]"
      :target sim/up!
      :args [:ip]
      :spec (merge ssh-spec
                   {:state     {:desc "State to start in (tools/shell/states.edn)" :coerce :string}
                    :http-port {:desc "Port the fake tier listens on" :coerce :long}})}

     "down"
     {:usage "Usage: shell sim down <ip>"
      :target sim/down!
      :args [:ip]
      :spec ssh-spec}}}})

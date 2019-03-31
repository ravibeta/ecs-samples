package ecs

import (
	"fmt"

	corev1 "k8s.io/api/core/v1"
)

// addSharedDir adds env var and volumes for shared dir when running kubelet in
// a container.
func (s *Deployment) addSharedDir(podSpec *corev1.PodSpec) {
	mountPropagationBidirectional := corev1.MountPropagationBidirectional
	nodeContainer := &podSpec.Containers[0]

	// If kubelet is running in a container, sharedDir should be set.
	if s.stos.Spec.SharedDir != "" {
		envVar := corev1.EnvVar{
			Name:  deviceDirEnvVar,
			Value: fmt.Sprintf("%s/devices", s.stos.Spec.SharedDir),
		}
		nodeContainer.Env = append(nodeContainer.Env, envVar)

		sharedDir := corev1.Volume{
			Name: "shared",
			VolumeSource: corev1.VolumeSource{
				HostPath: &corev1.HostPathVolumeSource{
					Path: s.stos.Spec.SharedDir,
				},
			},
		}
		podSpec.Volumes = append(podSpec.Volumes, sharedDir)

		volMnt := corev1.VolumeMount{
			Name:             "shared",
			MountPath:        s.stos.Spec.SharedDir,
			MountPropagation: &mountPropagationBidirectional,
		}
		nodeContainer.VolumeMounts = append(nodeContainer.VolumeMounts, volMnt)
	}
}

// addCSI adds the CSI env vars, volumes and containers to the provided podSpec.
func (s *Deployment) addCSI(podSpec *corev1.PodSpec) {
	hostpathDirOrCreate := corev1.HostPathDirectoryOrCreate
	hostpathDir := corev1.HostPathDirectory
	mountPropagationBidirectional := corev1.MountPropagationBidirectional

	nodeContainer := &podSpec.Containers[0]

	// Add CSI specific configurations if enabled.
	if s.stos.Spec.CSI.Enable {
		vols := []corev1.Volume{
			{
				Name: "registrar-socket-dir",
				VolumeSource: corev1.VolumeSource{
					HostPath: &corev1.HostPathVolumeSource{
						Path: s.stos.Spec.GetCSIRegistrarSocketDir(),
						Type: &hostpathDirOrCreate,
					},
				},
			},
			{
				Name: "kubelet-dir",
				VolumeSource: corev1.VolumeSource{
					HostPath: &corev1.HostPathVolumeSource{
						Path: s.stos.Spec.GetCSIKubeletDir(),
						Type: &hostpathDir,
					},
				},
			},
			{
				Name: "plugin-dir",
				VolumeSource: corev1.VolumeSource{
					HostPath: &corev1.HostPathVolumeSource{
						Path: s.stos.Spec.GetCSIPluginDir(CSIV1Supported(s.k8sVersion)),
						Type: &hostpathDirOrCreate,
					},
				},
			},
			{
				Name: "device-dir",
				VolumeSource: corev1.VolumeSource{
					HostPath: &corev1.HostPathVolumeSource{
						Path: s.stos.Spec.GetCSIDeviceDir(),
						Type: &hostpathDir,
					},
				},
			},
			{
				Name: "registration-dir",
				VolumeSource: corev1.VolumeSource{
					HostPath: &corev1.HostPathVolumeSource{
						Path: s.stos.Spec.GetCSIRegistrationDir(CSIV1Supported(s.k8sVersion)),
						Type: &hostpathDir,
					},
				},
			},
		}

		podSpec.Volumes = append(podSpec.Volumes, vols...)

		volMnts := []corev1.VolumeMount{
			{
				Name:             "kubelet-dir",
				MountPath:        s.stos.Spec.GetCSIKubeletDir(),
				MountPropagation: &mountPropagationBidirectional,
			},
			{
				Name:      "plugin-dir",
				MountPath: s.stos.Spec.GetCSIPluginDir(CSIV1Supported(s.k8sVersion)),
			},
			{
				Name:      "device-dir",
				MountPath: s.stos.Spec.GetCSIDeviceDir(),
			},
		}

		// Append volume mounts to the first container, the only container is the node container, at this point.
		nodeContainer.VolumeMounts = append(nodeContainer.VolumeMounts, volMnts...)

		envVar := []corev1.EnvVar{
			{
				Name:  csiEndpointEnvVar,
				Value: s.stos.Spec.GetCSIEndpoint(CSIV1Supported(s.k8sVersion)),
			},
		}

		// Append CSI Provision Creds env var if enabled.
		if s.stos.Spec.CSI.EnableProvisionCreds {
			envVar = append(
				envVar,
				corev1.EnvVar{
					Name:  csiRequireCredsCreateEnvVar,
					Value: "true",
				},
				corev1.EnvVar{
					Name:  csiRequireCredsDeleteEnvVar,
					Value: "true",
				},
				getCSICredsEnvVar(csiProvisionCredsUsernameEnvVar, csiProvisionerSecretName, "username"),
				getCSICredsEnvVar(csiProvisionCredsPasswordEnvVar, csiProvisionerSecretName, "password"),
			)
		}

		// Append CSI Controller Publish env var if enabled.
		if s.stos.Spec.CSI.EnableControllerPublishCreds {
			envVar = append(
				envVar,
				corev1.EnvVar{
					Name:  csiRequireCredsCtrlPubEnvVar,
					Value: "true",
				},
				corev1.EnvVar{
					Name:  csiRequireCredsCtrlUnpubEnvVar,
					Value: "true",
				},
				getCSICredsEnvVar(csiControllerPubCredsUsernameEnvVar, csiControllerPublishSecretName, "username"),
				getCSICredsEnvVar(csiControllerPubCredsPasswordEnvVar, csiControllerPublishSecretName, "password"),
			)
		}

		// Append CSI Node Publish env var if enabled.
		if s.stos.Spec.CSI.EnableNodePublishCreds {
			envVar = append(
				envVar,
				corev1.EnvVar{
					Name:  csiRequireCredsNodePubEnvVar,
					Value: "true",
				},
				getCSICredsEnvVar(csiNodePubCredsUsernameEnvVar, csiNodePublishSecretName, "username"),
				getCSICredsEnvVar(csiNodePubCredsPasswordEnvVar, csiNodePublishSecretName, "password"),
			)
		}

		// Append env vars to the first container, node container.
		nodeContainer.Env = append(nodeContainer.Env, envVar...)

		driverReg := corev1.Container{
			Image:           s.stos.Spec.GetCSINodeDriverRegistrarImage(CSIV1Supported(s.k8sVersion)),
			Name:            "csi-driver-registrar",
			ImagePullPolicy: corev1.PullIfNotPresent,
			Args: []string{
				"--v=5",
				"--csi-address=$(ADDRESS)",
			},
			Env: []corev1.EnvVar{
				{
					Name:  addressEnvVar,
					Value: "/csi/csi.sock",
				},
				{
					Name: kubeNodeNameEnvVar,
					ValueFrom: &corev1.EnvVarSource{
						FieldRef: &corev1.ObjectFieldSelector{
							APIVersion: "v1",
							FieldPath:  "spec.nodeName",
						},
					},
				},
			},
			VolumeMounts: []corev1.VolumeMount{
				{
					Name:      "plugin-dir",
					MountPath: "/csi",
				},
				{
					Name:      "registrar-socket-dir",
					MountPath: "/var/lib/csi/sockets/",
				},
				{
					Name:      "registration-dir",
					MountPath: "/registration",
				},
			},
		}

		// Add extra flags to activate node-register mode if kubelet plugins
		// watcher is supported.
		if kubeletPluginsWatcherSupported(s.k8sVersion) {
			driverReg.Args = append(
				driverReg.Args,
				fmt.Sprintf("--kubelet-registration-path=%s", s.stos.Spec.GetCSIKubeletRegistrationPath(CSIV1Supported(s.k8sVersion))))
		}
		podSpec.Containers = append(podSpec.Containers, driverReg)

		if CSIV1Supported(s.k8sVersion) {
			livenessProbe := corev1.Container{
				Image:           s.stos.Spec.GetCSILivenessProbeImage(),
				Name:            "csi-liveness-probe",
				ImagePullPolicy: corev1.PullIfNotPresent,
				Args: []string{
					"--csi-address=$(ADDRESS)",
					"--connection-timeout=3s",
				},
				Env: []corev1.EnvVar{
					{
						Name:  addressEnvVar,
						Value: "/csi/csi.sock",
					},
				},
				VolumeMounts: []corev1.VolumeMount{
					{
						Name:      "plugin-dir",
						MountPath: "/csi",
					},
				},
			}
			podSpec.Containers = append(podSpec.Containers, livenessProbe)
		}
	}
}

// addNodeAffinity adds node affinity to the given pod spec from the cluster
// spec NodeSelectorLabel.
func (s *Deployment) addNodeAffinity(podSpec *corev1.PodSpec) {
	if len(s.stos.Spec.NodeSelectorTerms) > 0 {
		podSpec.Affinity = &corev1.Affinity{NodeAffinity: &corev1.NodeAffinity{
			RequiredDuringSchedulingIgnoredDuringExecution: &corev1.NodeSelector{
				NodeSelectorTerms: s.stos.Spec.NodeSelectorTerms,
			},
		}}
	}
}

// addTolerations adds tolerations to the given pod spec from cluster
// spec Tolerations.
func (s *Deployment) addTolerations(podSpec *corev1.PodSpec) error {
	tolerations := s.stos.Spec.Tolerations
	for i := range tolerations {
		if tolerations[i].Operator == corev1.TolerationOpExists && tolerations[i].Value != "" {
			return fmt.Errorf("key(%s): toleration value must be empty when `operator` is 'Exists'", tolerations[i].Key)
		}
	}
	if len(tolerations) > 0 {
		podSpec.Tolerations = s.stos.Spec.Tolerations
	}
	return nil
}
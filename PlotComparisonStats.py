# -*- coding: utf-8 -*-
"""
Created on Mon Oct  7 21:52:04 2024

@author: bnwer
"""
import os
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
from matplotlib.backends.backend_pdf import PdfPages


def get_data(file_path):
    data = {}
    with open(file_path, 'r') as f:
        lines = f.readlines()

        metric = None
        for line in lines:

            line = line.strip()

            if line.startswith("Metric:"):
                metric = line.split(":")[1].strip()

            elif line.startswith("Technique:"):
                line_split = line.split(":")

                technique, values_str = line_split[1].strip(), line_split[2].strip()

                if metric not in data:
                    data[metric] = {}

                if technique not in data[metric]:
                    data[metric][technique] = []

                data[metric][technique].extend([float(num.strip()) for num in values_str.split(',')])

    return data


current_path = 'PATH_TO_RESULTS'
data_dir = os.path.join(os.path.dirname(current_path), '')
output_dir = os.path.join(os.path.dirname(current_path), '')


def main():
    datafiles = os.listdir(data_dir)
    agg = 'Data'
    # suffix, discriminator_order, fig_size, discriminator_labels = \
    #     'Stopping', ['Chernoff', 'ConfidenceInterval'], (4, 3.5), \
    #     ['Chernoff', 'Conf. Int.']
    # suffix, discriminator_order, fig_size, discriminator_labels = \
    #    'Selection', ['NonCorrupt', 'Minimum', 'Random'], (5.5, 3.5), \
    #    ['?x/?y', 'Min', 'Rnd']
    # suffix, discriminator_order, fig_size, discriminator_labels = \
    #    'Accuracy', ['Acc0.05', 'Acc0.1', 'Acc0.15'], (5.5, 3.5), \
    #    [r'$\epsilon$=0.05', r'$\epsilon$=0.10', r'$\epsilon$=0.15']
    suffix, discriminator_order, fig_size, discriminator_labels = \
        'Confidence', ['Conf0.01', 'Conf0.05', 'Conf0.1'], (5.5, 3.5), \
        [r'$\alpha$=0.01', r'$\alpha$=0.05', r'$\alpha$=0.10']

    tech_order = ['AnyBURL',  # 'Chao1',
                  'Chao2',  # 'Coverage', 'Jackknife',
                  'Poisson', 'Binomial',
                  'Hypergeometric', 'HansenHurwitz', 'HorvitzThompsonWith']

    # Only the colors we need!
    bright_palette = sns.color_palette("pastel")[:len(tech_order)]

    print(sns.color_palette("pastel").as_hex())

    if suffix != 'Selection':
        bright_palette.pop(tech_order.index('AnyBURL'))  # AnyBURL only has Selection!
        tech_order.remove('AnyBURL')

    file_filters = []

    for limit in ['Chernoff', 'ConfidenceInterval']:
        for acc in ['0.05', '0.1', '0.15']:
            for conf in ['0.01', '0.05', '0.1']:
                file_filters.append(f'_{agg}_{limit}_Acc{acc}_Conf{conf}.txt')
                for select in ['Corrupt', 'NonCorrupt', 'Minimum', 'Maximum', 'Random']:
                    file_filters.append(f'_{agg}_{limit}_{select}_Acc{acc}_Conf{conf}.txt')

    # To reduce memory, we'll process one metric at a time.
    metrics = set()

    for file_filter in file_filters:
        for file in datafiles:
            if not file.endswith(file_filter):
                continue

            file_path = os.path.join(data_dir, file)
            name = file.replace(file_filter, '')

            data = get_data(file_path)

            # Change name to be plotted.
            name = name.replace('_', '-')

            if name == 'FB15K237':
                name = 'FB15K-237'

            # Skipping Hetionet!
            if name == 'Hetionet':
                continue

            # This is {Support/PCA}_Value or {Support/PCA}_CPU,
            #   we get only Value or CPU.
            for m in data.keys():
                metrics.add(m.split('_')[1])

    for m in metrics:
        boxplot_data_by_metrics = []

        for file_filter in file_filters:
            for file in datafiles:
                if not file.endswith(file_filter):
                    continue

                print(file)
                file_path = os.path.join(data_dir, file)
                name = file.replace(file_filter, '')

                data = get_data(file_path)

                # Change name to be plotted.
                name = name.replace('_', '-')

                if name == 'FB15K237':
                    name = 'FB15K-237'

                # Skipping Hetionet!
                if name == 'Hetionet':
                    continue

                for m_in_data in data.keys():
                    if m in m_in_data:
                        for technique in data[m_in_data].keys():
                            values = data[m_in_data][technique]

                            t_split = technique.split('_')

                            t = t_split[1]

                            t_in_disc = [x for x in t_split if x in discriminator_order]

                            if len(t_in_disc) == 0:
                                continue

                            # Should be only one.
                            discriminator = t_in_disc[0]

                            if len(discriminator) == 0:
                                continue

                            boxplot_data_by_metrics.append(
                                {'Dataset': name, 'Metric': m,
                                 'Technique': t, 'Values': values,
                                 'Discriminator': discriminator})

        # Let's plot!
        df = pd.DataFrame(boxplot_data_by_metrics)
        df = df.explode('Values')

        plt.figure(figsize=fig_size)
        plt.rcParams.update({'font.size': 11})

        sns.set_palette('bright')
        sns.boxenplot(data=df, x='Discriminator', order=discriminator_order,
                      y='Values', hue='Technique', hue_order=tech_order, palette=bright_palette,
                      width=0.75, k_depth='proportion', outlier_prop=0.0001,
                      legend=False, linecolor='black')

        # sns.violinplot(data=df, x='Discriminator', order=discriminator_order,
        #               y='Values', hue='Technique', hue_order=tech_order, palette=bright_palette,
        #               width=0.75, legend=False, linecolor='black', split=True, inner='quart')

        positions = range(len(discriminator_order))
        for pos in positions:
            plt.axvline(x=pos+0.5, color='black', linestyle='-', linewidth=0.5)

        plt.grid(axis='y', linestyle='--', linewidth=0.25)

        if 'CPU' in m:
            plt.ylim(0, 1000000)
        else:
            # Highlight 0
            plt.axhline(y=0, color='black', linestyle='--', linewidth=1.0)

        if 'PCAError' in m:
            plt.ylim(-1.0, 1.0)
        else:
            plt.yscale('symlog')

        xticks_positions, xticks_text = plt.xticks()

        plt.xticks(
            ticks=xticks_positions,
            labels=discriminator_labels,
            rotation=25,  # Rotates x-axis ticks by x-degrees
        )

        # Only for the first and third charts.
        if suffix == 'Stopping' or suffix == 'Confidence':
            if 'CPU' in m:
                plt.ylabel('Speedup')
            elif 'Error' in m:
                plt.ylabel('Conf. Error')
            else:
                plt.ylabel('q-error')
        else:
            # plt.gca().axes.set_yticklabels([])
            plt.ylabel('')

        plt.xlabel('')

        plt.tight_layout()

        pdf_path = os.path.join(output_dir, f'{m}_{suffix}.pdf')
        with PdfPages(pdf_path, keep_empty=False) as pdf:
            pdf.savefig()
            pdf.close()


if __name__ == "__main__":
    main()
